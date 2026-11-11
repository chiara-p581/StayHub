package com.stayhub.integracion;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSRuntimeException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Entorno compartido de las pruebas de integración: un PostgreSQL real y un broker Artemis real,
 * ambos en contenedores (Testcontainers). Se levantan una sola vez por corrida, la primera vez
 * que alguna prueba los pide, y Testcontainers los elimina al terminar la JVM.
 *
 * <ul>
 *   <li>PostgreSQL: misma imagen que compose.yaml y el mismo esquema que usa la app
 *       (docker/postgres/01-schema.sql). Hibernate corre con hbm2ddl=validate, así que si una
 *       entidad no coincide con el esquema, las pruebas fallan.</li>
 *   <li>Artemis: broker real; las colas y tópicos se crean solos al usarlos.</li>
 * </ul>
 */
public final class EntornoIntegracion {

    /** Misma imagen que compose.yaml. */
    private static final String IMAGEN_POSTGRES = "postgres:18.6-alpine3.24";
    /** Misma versión que el cliente artemis-jakarta-client del pom (propiedad artemis.version). */
    private static final String IMAGEN_ARTEMIS = "apache/activemq-artemis:2.44.0-alpine";

    private static final String DB_NOMBRE = "stayhub";
    private static final String DB_USUARIO = "stayhub";
    private static final String DB_PASSWORD = "stayhub_it_2026";

    private static final String JMS_USUARIO = "stayhub";
    private static final String JMS_PASSWORD = "stayhub_it_2026";

    private static GenericContainer<?> postgres;
    private static GenericContainer<?> artemis;
    private static EntityManagerFactory emf;
    private static ConnectionFactory fabricaJms;

    private EntornoIntegracion() { }

    // ---------------------------------------------------------------- PostgreSQL

    private static synchronized GenericContainer<?> postgres() {
        if (postgres == null) {
            GenericContainer<?> contenedor = new GenericContainer<>(DockerImageName.parse(IMAGEN_POSTGRES))
                    .withEnv("POSTGRES_DB", DB_NOMBRE)
                    .withEnv("POSTGRES_USER", DB_USUARIO)
                    .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                    .withCopyFileToContainer(MountableFile.forHostPath(rutaEsquema().toString()),
                            "/docker-entrypoint-initdb.d/01-schema.sql")
                    .withExposedPorts(5432)
                    // PostgreSQL imprime este mensaje dos veces: al terminar el init y al quedar definitivo
                    .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
                            .withStartupTimeout(Duration.ofMinutes(3)));
            contenedor.start();
            postgres = contenedor;
        }
        return postgres;
    }

    private static Path rutaEsquema() {
        Path base = Path.of("").toAbsolutePath();
        for (Path candidato : List.of(
                base.resolve("../docker/postgres/01-schema.sql"),   // mvn corrido desde proyecto/
                base.resolve("docker/postgres/01-schema.sql"))) {   // mvn corrido desde la raíz del repo
            if (Files.exists(candidato)) return candidato.normalize();
        }
        throw new IllegalStateException("No se encontró docker/postgres/01-schema.sql desde " + base);
    }

    /** Unidad de persistencia StayHubIT (src/test/resources/META-INF/persistence.xml) contra el PostgreSQL real. */
    public static synchronized EntityManagerFactory emf() {
        if (emf == null) {
            GenericContainer<?> db = postgres();
            Map<String, Object> propiedades = new HashMap<>();
            propiedades.put("jakarta.persistence.jdbc.url",
                    "jdbc:postgresql://" + db.getHost() + ":" + db.getMappedPort(5432) + "/" + DB_NOMBRE);
            propiedades.put("jakarta.persistence.jdbc.user", DB_USUARIO);
            propiedades.put("jakarta.persistence.jdbc.password", DB_PASSWORD);
            propiedades.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
            // Igual que en producción: el esquema lo crea 01-schema.sql y Hibernate solo lo valida
            propiedades.put("hibernate.hbm2ddl.auto", "validate");
            propiedades.put("jakarta.persistence.validation.mode", "none");
            emf = Persistence.createEntityManagerFactory("StayHubIT", propiedades);
        }
        return emf;
    }

    /** Ejecuta la acción en una transacción propia (commit si sale bien, rollback si falla). */
    public static <T> T enTransaccion(Function<EntityManager, T> accion) {
        EntityManager em = emf().createEntityManager();
        try {
            em.getTransaction().begin();
            T resultado = accion.apply(em);
            em.getTransaction().commit();
            return resultado;
        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Consulta de solo lectura (cuenta filas, etc.) en una transacción propia. */
    public static long contar(String sqlNativo, String parametro, Object valor) {
        return enTransaccion(em -> ((Number) em.createNativeQuery(sqlNativo)
                .setParameter(parametro, valor).getSingleResult()).longValue());
    }

    // ---------------------------------------------------------------- Artemis

    public static synchronized ConnectionFactory fabricaJms() {
        if (fabricaJms == null) {
            GenericContainer<?> contenedor = new GenericContainer<>(DockerImageName.parse(IMAGEN_ARTEMIS))
                    .withEnv("ARTEMIS_USER", JMS_USUARIO)
                    .withEnv("ARTEMIS_PASSWORD", JMS_PASSWORD)
                    .withExposedPorts(61616)
                    .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
            contenedor.start();
            artemis = contenedor;

            ActiveMQConnectionFactory fabrica = new ActiveMQConnectionFactory(
                    "tcp://" + contenedor.getHost() + ":" + contenedor.getMappedPort(61616),
                    JMS_USUARIO, JMS_PASSWORD);
            // Los mensajes de StayHub son ObjectMessage con records Serializable: Artemis exige
            // declarar qué paquetes se pueden deserializar.
            fabrica.setDeserializationAllowList("com.stayhub,java.lang,java.util,java.time,java.math");
            esperarBroker(fabrica);
            fabricaJms = fabrica;
        }
        return fabricaJms;
    }

    /** El puerto puede abrirse un instante antes de que el broker acepte sesiones: reintenta hasta 60 s. */
    private static void esperarBroker(ConnectionFactory fabrica) {
        JMSRuntimeException ultimo = null;
        for (int intento = 0; intento < 60; intento++) {
            try (JMSContext contexto = fabrica.createContext()) {
                contexto.createQueue("sonda-arranque");
                return;
            } catch (JMSRuntimeException ex) {
                ultimo = ex;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupcion) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrumpido esperando a Artemis", interrupcion);
                }
            }
        }
        throw new IllegalStateException("Artemis no aceptó conexiones a tiempo", ultimo);
    }

    // ---------------------------------------------------------------- utilidades

    /** Inyecta un campo privado (los componentes usan @Inject/@PersistenceContext, que acá no existen). */
    public static void inyectar(Object objetivo, String campo, Object valor) {
        try {
            var f = objetivo.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(objetivo, valor);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("No se pudo inyectar " + campo + " en " + objetivo.getClass(), ex);
        }
    }
}