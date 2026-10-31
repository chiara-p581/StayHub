FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY proyecto/pom.xml ./pom.xml
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY proyecto/src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM quay.io/wildfly/wildfly:41.0.0.Final-jdk21

USER root
RUN microdnf install --best --nodocs -y curl && microdnf clean all && \
    mkdir -p /opt/jboss/wildfly/modules/org/postgresql/main && \
    mkdir -p /opt/jboss/local-security && \
    curl --fail --location --output /opt/jboss/wildfly/modules/org/postgresql/main/postgresql.jar \
      https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar && \
    chown -R jboss:root /opt/jboss/wildfly/modules/org/postgresql /opt/jboss/local-security

COPY --chown=jboss:root docker/wildfly/module.xml /opt/jboss/wildfly/modules/org/postgresql/main/module.xml
COPY --chown=jboss:root docker/wildfly/configure.cli /opt/jboss/wildfly/configure.cli
COPY --chown=jboss:root docker/wildfly/load-demo.sh /opt/jboss/load-demo.sh

USER jboss
RUN keytool -genkeypair -alias stayhub-local -keyalg RSA -keysize 3072 -validity 825 \
      -storetype PKCS12 -keystore /opt/jboss/local-security/stayhub-local.p12 \
      -storepass stayhub-local -keypass stayhub-local \
      -dname "CN=localhost, OU=StayHub Local, O=StayHub, C=AR" \
      -ext "SAN=dns:localhost,ip:127.0.0.1" -noprompt && \
    keytool -genkeypair -alias stayhub-pms -keyalg RSA -keysize 3072 -validity 825 \
      -storetype PKCS12 -keystore /opt/jboss/local-security/stayhub-pms-client.p12 \
      -storepass stayhub-pms-local -keypass stayhub-pms-local \
      -dname "CN=stayhub-pms-demo, OU=PMS, O=StayHub, C=AR" -noprompt && \
    keytool -exportcert -alias stayhub-pms \
      -keystore /opt/jboss/local-security/stayhub-pms-client.p12 \
      -storepass stayhub-pms-local -rfc \
      -file /opt/jboss/local-security/stayhub-pms-client.cer && \
    keytool -importcert -alias stayhub-pms -noprompt \
      -file /opt/jboss/local-security/stayhub-pms-client.cer \
      -storetype PKCS12 -keystore /opt/jboss/local-security/stayhub-pms-trust.p12 \
      -storepass stayhub-pms-local && \
    /opt/jboss/wildfly/bin/jboss-cli.sh --file=/opt/jboss/wildfly/configure.cli && \
    fingerprint="$(keytool -J-Duser.language=en -list -v -alias stayhub-pms \
      -keystore /opt/jboss/local-security/stayhub-pms-client.p12 \
      -storepass stayhub-pms-local | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1)" && \
    test -n "$fingerprint" && \
    printf '%s\n' \
      'embed-server --server-config=standalone-full.xml --std-out=echo' \
      "/system-property=stayhub.pms.certificado-sha256:add(value=\"$fingerprint\")" \
      '/system-property=stayhub.pms.hoteles:add(value="1,2,3,4,5,6,7,8,9,10")' \
      'stop-embedded-server' > /tmp/configure-pms-security.cli && \
    /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/configure-pms-security.cli && \
    chmod +x /opt/jboss/load-demo.sh

COPY --from=build --chown=jboss:root /build/target/StayHub.war /opt/jboss/wildfly/standalone/deployments/StayHub.war

EXPOSE 8443
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
  CMD curl --insecure --fail --silent https://localhost:8443/StayHub/api/hoteles >/dev/null || exit 1

CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "0.0.0.0"]
