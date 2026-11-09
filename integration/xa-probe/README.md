# Sonda de integración XA

Instrumental temporal para WildFly local. No forma parte de la compilación ni
del WAR de StayHub y no expone rutas HTTP. Requiere PostgreSQL con transacciones
preparadas habilitadas, `java:/PostgresDS` XA y `java:/JmsXA`.

La sonda crea `stayhub_xa_probe_20261109` y dos destinos JMS exclusivos.
El despliegue falla si la tabla ya existe: investigar una ejecución previa
antes de volver a correrla. No ejecutarla simultáneamente desde varios clientes.

## Casos

1. INSERT y publicación JMS confirmados juntos.
2. Publicación JMS seguida de violación de clave primaria real en PostgreSQL:
   el mensaje se revierte.
3. INSERT seguido de rechazo real del broker por dirección de prueba llena:
   la fila se revierte.
4. INSERT y publicación seguidos de excepción de aplicación: ambos se revierten.

Al final se exige una única fila (`id=1`) y un único mensaje con correlación
`xa-commit-20261109`. Luego se elimina la tabla de prueba y se drenan las dos
colas. Las excepciones y mensajes ERROR de los casos negativos son esperados.
Revisar todos los marcadores `XA_PROBE PASS` y `XA_PROBE CLEANUP`, no solo el
estado del despliegue. La prueba no simula un corte de energía durante recovery.

## Repetición

Desde la raíz de StayHub, definir `STAYHUB_WILDFLY` con la ruta de WildFly y
`STAYHUB_JAKARTA_API` con la ruta del JAR `jakarta.jakartaee-api-10.0.0.jar`.
Compilar en un directorio temporal y empaquetar solamente WEB-INF:

```sh
STAYHUB_PROBE_BUILD=$(mktemp -d /tmp/stayhub-xa-build.XXXXXX)
mkdir -p "$STAYHUB_PROBE_BUILD/WEB-INF/classes"
cp integration/xa-probe/WEB-INF/beans.xml "$STAYHUB_PROBE_BUILD/WEB-INF/beans.xml"
javac -cp "$STAYHUB_JAKARTA_API" -d "$STAYHUB_PROBE_BUILD/WEB-INF/classes" integration/xa-probe/src/XaWorker.java integration/xa-probe/src/XaScenario.java
jar --create --file "$STAYHUB_PROBE_BUILD/stayhub-xa-probe.war" -C "$STAYHUB_PROBE_BUILD" WEB-INF
"$STAYHUB_WILDFLY/bin/jboss-cli.sh" --connect --file=integration/xa-probe/setup.cli
"$STAYHUB_WILDFLY/bin/jboss-cli.sh" --connect --command="deploy $STAYHUB_PROBE_BUILD/stayhub-xa-probe.war"
```

Verificar el log de WildFly y las métricas
`/subsystem=datasources/xa-data-source=StayHubDS/statistics=pool`:
debe aumentar `XAPrepareCount` y aparecer commit y rollback reales.
Comprobar que `pg_prepared_xacts` no conserve transacciones de la sonda.

Después de verificar `XA_PROBE CLEANUP`, retirar el despliegue y sus recursos:

```sh
"$STAYHUB_WILDFLY/bin/jboss-cli.sh" --connect --command="undeploy stayhub-xa-probe.war"
"$STAYHUB_WILDFLY/bin/jboss-cli.sh" --connect --file=integration/xa-probe/cleanup.cli
```

Si una aserción falla, el cleanup automático no corre: conservar la evidencia,
inspeccionar únicamente los recursos de prueba y resolver su estado antes de
retirarlos. No ejecutar purgas sobre colas de negocio ni rollback de
transacciones preparadas ajenas.
