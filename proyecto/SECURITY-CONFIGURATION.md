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
cuenta de la aplicación, en orden los scripts de `src/main/resources/db/migration/` (actualmente V2 y V3).
Hibernate quedó en modo `validate`: la aplicación ya no modifica el esquema al iniciar. V3 vincula la idempotencia al usuario, agrega restricciones de monto/moneda/estado e índices operativos.

## Verificaciones del build

`mvn verify` genera el SBOM CycloneDX. El análisis de vulnerabilidades se ejecuta con
`mvn org.owasp:dependency-check-maven:check`; el build falla a partir de CVSS 8.

## Cadena de suministro y contenido de terceros (SEC-11)

Las fuentes y Material Symbols están autoalojadas en `src/main/webapp/assets/fonts`; sus licencias y hashes SHA-256 se conservan junto a los artefactos. El frontend no carga scripts, fuentes ni imágenes desde terceros y la CSP permite recursos visuales únicamente desde el mismo origen. Dependabot se configura en la raíz del repositorio para Maven, npm y GitHub Actions. El workflow `Security supply chain` ejecuta pruebas, genera SBOM CycloneDX, corre OWASP Dependency-Check y publica los reportes. `npm audit --audit-level=high` debe quedar en cero; las alertas moderadas de herramientas de build se revisan durante cada actualización y no se empaquetan en el WAR.

## Persistencia y pagos (SEC-12)

La cuenta de la aplicación sólo necesita permisos DML sobre el esquema ya migrado; no debe poseer `CREATE`, `ALTER`, `DROP`, `TRUNCATE` ni privilegios de administración. Las migraciones se ejecutan con una identidad separada y luego Hibernate valida el esquema. El pago bloquea las reservas dentro de la transacción, verifica propietario/estado/monto/moneda del servidor e identifica reintentos por `(usuario_id, clave_idempotencia)`. La migración V3 añade restricciones e índices defensivos. Los backups, cifrado y aislamiento de red pertenecen a la operación de PostgreSQL y deben verificarse antes de producción.

## Auditoría y errores (SEC-13)

Toda respuesta REST de error pasa por `RespuestaSeguraFilter`: agrega correlación, evita caché, registra una decisión estructurada una sola vez y reemplaza cualquier cuerpo 5xx por un mensaje genérico. Los logs no incluyen cuerpos, contraseñas, tokens, PAN/CVV ni mensajes arbitrarios de excepciones. `config/seguridad-a-logging.cli` separa auditoría y errores en archivos rotados de hasta 10 MiB con 14 respaldos. El acceso, envío y retención posterior de esos archivos debe ajustarse a la política del ambiente.

## Seguridad de sesiones (KAN-101)

StayHub autentica con **JWT** (HS256, `TokenJwt` + `MecanismoJwt`). Al hacer login se emite
un token con `iss=stayhub`, `aud=stayhub-api`, `sub` (usuario), `rol`, `mfa`, `auth_time`, `iat`,
`exp` (15 minutos) y `jti`. El navegador lo recibe en la cookie `STAYHUB_TOKEN` (`HttpOnly`,
`SameSite=Lax` y `Secure` fuera del modo HTTP local); los clientes de API pueden enviarlo en
`Authorization: Bearer <token>` (en ese caso no se pide token CSRF, porque el navegador no lo
manda solo).

Un token vencido, con la firma alterada, con `alg=none` u otro algoritmo, de otro emisor u
otra audiencia, revocado o con más de ocho horas desde el login recibe **401 `TOKEN_INVALIDO`**
(`TokenJwtTest`). El logout revoca el `jti` en el servidor y borra la cookie. Mientras se usa,
el token se renueva solo cuando le quedan menos de cinco minutos.

La clave se configura con la propiedad `stayhub.jwt.secreto` o la variable `STAYHUB_JWT_SECRETO`
(Base64, 32 bytes o más) y nunca se versiona; si falta, WildFly genera una aleatoria al arrancar.
La sesión HTTP (`JSESSIONID`) ya no guarda la identidad: solo el token CSRF y el paso pendiente
de MFA durante el login, y se rota en cada login. Las mutaciones usan
un token CSRF asociado a esa misma sesión.

La suite automatizada verifica cookie manipulada, rotación al iniciar sesión, reutilización
posterior al logout, aislamiento entre usuarios y vencimiento absoluto. Una sesión inválida,
ausente o expirada recibe HTTP 401 sin revelar datos internos.