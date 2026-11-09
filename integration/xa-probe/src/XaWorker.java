package probe;
import jakarta.annotation.Resource;
import jakarta.ejb.*;
import jakarta.jms.*;
import javax.sql.DataSource;
import java.lang.IllegalStateException;

/** Instrumental de integración; nunca se incluye en el WAR de StayHub. */
@Stateless
public class XaWorker {
  @Resource(lookup="java:/PostgresDS") DataSource ds;
  @Resource(lookup="java:/JmsXA") ConnectionFactory cf;
  @Resource(lookup="java:/jms/queue/StayHubXaProbe") Queue queue;
  @Resource(lookup="java:/jms/queue/StayHubXaRejectedProbe") Queue rejected;

  @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
  public void setup() throws Exception {
    try(var c=ds.getConnection();var s=c.createStatement()) {
      s.execute("CREATE TABLE stayhub_xa_probe_20261109 (id integer PRIMARY KEY)");
    }
    // Saturar exclusivamente una cola de prueba: el broker debe rechazar nuevos envíos.
    try(var context=cf.createContext()) {
      for(int i=0;i<4;i++) {
        try { context.createProducer().send(rejected,"x".repeat(16384)); }
        catch(JMSRuntimeException ex) { System.out.println("XA_PROBE broker rejection primed="+ex.getClass().getSimpleName()); break; }
      }
    }
  }
  public void commitBoth() throws Exception {
    insert(1);
    try(var context=cf.createContext()) { context.createProducer().setJMSCorrelationID("xa-commit-20261109").send(queue,"commit"); }
  }
  public void databaseFailure() throws Exception {
    try(var context=cf.createContext()) { context.createProducer().setJMSCorrelationID("xa-db-fail-20261109").send(queue,"must rollback"); }
    try { insert(1); } // Violación real de PK después del envío JMS.
    catch(java.sql.SQLException ex) { throw new EJBException("XA_PROBE actual DB constraint failure",ex); }
  }
  public void brokerFailure() throws Exception {
    insert(3);
    try(var context=cf.createContext()) { context.createProducer().send(rejected,"x".repeat(16384)); }
    throw new IllegalStateException("BROKER_DID_NOT_REJECT");
  }
  public void applicationFailure() throws Exception {
    insert(4);
    try(var context=cf.createContext()) { context.createProducer().setJMSCorrelationID("xa-app-fail-20261109").send(queue,"must rollback"); }
    throw new EJBException("XA_PROBE controlled rollback after both writes");
  }
  private void insert(int id) throws Exception {
    try(var c=ds.getConnection();var s=c.prepareStatement("INSERT INTO stayhub_xa_probe_20261109(id) VALUES (?)")) { s.setInt(1,id);s.executeUpdate(); }
  }
  @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
  public void assertState() throws Exception {
    try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*), min(id), max(id) FROM stayhub_xa_probe_20261109")) {
      r.next(); if(r.getInt(1)!=1||r.getInt(2)!=1||r.getInt(3)!=1) throw new IllegalStateException("Unexpected DB rows");
    }
    try(var context=cf.createContext();var browser=context.createBrowser(queue)) {
      var messages=browser.getEnumeration();int n=0;
      while(messages.hasMoreElements()) { var message=(Message)messages.nextElement(); if(!"xa-commit-20261109".equals(message.getJMSCorrelationID())) throw new IllegalStateException("Rolled-back JMS message persisted"); n++; }
      if(n!=1) throw new IllegalStateException("Expected exactly one committed JMS message: "+n);
    }
    System.out.println("XA_PROBE PASS final-state: DB only id=1; JMS only commit message");
  }
  @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
  public void cleanup() throws Exception {
    try(var c=ds.getConnection();var s=c.createStatement()) { s.execute("DROP TABLE stayhub_xa_probe_20261109"); }
    try(var context=cf.createContext();var consumer=context.createConsumer(queue);var rejectConsumer=context.createConsumer(rejected)) {
      while(consumer.receive(250)!=null) { }
      while(rejectConsumer.receive(250)!=null) { }
    }
    System.out.println("XA_PROBE CLEANUP table dropped and test queues drained");
  }
}
