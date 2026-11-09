package probe;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;

/** Ejecuta cuatro escenarios y exige exactamente un efecto persistente por recurso. */
@Singleton @Startup
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class XaScenario {
  @EJB XaWorker worker;
  @PostConstruct public void run() {
    try {
      worker.setup();
      worker.commitBoth();
      System.out.println("XA_PROBE PASS commitBoth");
      try { worker.databaseFailure(); throw new IllegalStateException("DB_DID_NOT_FAIL"); }
      catch(EJBException expected) { System.out.println("XA_PROBE PASS actual DB failure: "+expected.getClass().getSimpleName()); }
      try { worker.brokerFailure(); throw new IllegalStateException("BROKER_DID_NOT_FAIL"); }
      catch(EJBException expected) {
        Throwable cause=expected;
        while(cause.getCause()!=null) cause=cause.getCause();
        if(!cause.getClass().getSimpleName().contains("AddressFull")) throw expected;
        System.out.println("XA_PROBE PASS actual broker rejection: "+cause.getClass().getSimpleName());
      }
      try { worker.applicationFailure(); throw new IllegalStateException("APP_DID_NOT_FAIL"); }
      catch(EJBException expected) { System.out.println("XA_PROBE PASS controlled application rollback"); }
      worker.assertState();
      worker.cleanup();
    } catch(Exception e) { throw new EJBException("XA_PROBE FAIL",e); }
  }
}
