# Configuración de los controles SEC-07 a SEC-12

## Webhooks OTA firmados

Cada canal debe tener un secreto distinto de al menos 32 caracteres:

`STAYHUB_CANALES_BOOKING_WEBHOOK_SECRET`, `STAYHUB_CANALES_EXPEDIA_WEBHOOK_SECRET`, etc.

El proveedor envía `X-StayHub-Timestamp` (epoch seconds), `X-StayHub-Nonce` y
`X-StayHub-Signature` (HMAC-SHA256 hexadecimal). El contenido firmado es:

`timestamp + "\n" + nonce + "\n" + method + "\n" + normalizedPath + "\n" + sha256Hex(body)`

La ventana admitida es de cinco minutos y cada nonce se consume una sola vez.

## Destinos permitidos

Definir `STAYHUB_CANALES_HOSTS_PERMITIDOS` como una lista separada por comas con los
hosts exactos de OTA y PMS. En producción solo se acepta HTTPS. Para un mock local puede
habilitarse expresamente `STAYHUB_CANALES_PERMITIR_HTTP_LOCAL=true` y agregar `localhost`
a la lista. Esta validación de aplicación debe complementarse con reglas de egress.

## Esquema de base de datos

Antes de desplegar el WAR se debe ejecutar, con una cuenta de migraciones separada de la
cuenta de la aplicación, `src/main/resources/db/migration/V2__seguridad_pagos_idempotentes.sql`.
Hibernate quedó en modo `validate`: la aplicación ya no modifica el esquema al iniciar.

## Verificaciones del build

`mvn verify` genera el SBOM CycloneDX. El análisis de vulnerabilidades se ejecuta con
`mvn org.owasp:dependency-check-maven:check`; el build falla a partir de CVSS 8.
