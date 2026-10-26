# Configuración local de seguridad — estado y activación

## Controles KAN-47 a KAN-51 — 26 de octubre de 2026

Antes del próximo despliegue se debe aplicar `config/seguridad-controles-schema.sql`
y configurar un secreto TOTP por cada cuenta administradora. El procedimiento,
incluido el generador que guarda el secreto fuera de Git, está en
`docs/IMPLEMENTACION_KAN_47_51.md`. Sin estas tablas el rate limiting falla
cerrado; sin el secreto correspondiente el login administrativo responde 503.

## Actualización de puertos — 16 de octubre de 2026

La configuración de desarrollo usa **https://localhost:8080/StayHub/index.html**.
HTTP pasa al puerto 8081 y redirige la navegación a HTTPS en 8080.
`stayhub.seguridad.http-local=false` conserva la exigencia de TLS y cookies Secure.
El script de reproducción configura estos puertos. Para servidores que ya tengan
TLS `StayHubTLS`, aplicar `config/https-8080.cli` y recargar WildFly.
Los puertos persisten en el servidor de cada persona; el repositorio conserva las
instrucciones y scripts para reproducirlos. Las referencias a 8443 de la sección
siguiente describen la configuración histórica del 6 de octubre.

## Estado al 6 de octubre de 2026

La migración se verificó directamente en PostgreSQL en modo de solo lectura:
columna `reserva.usuario_id` presente, FK `fk_reserva_usuario_seguridad_a`
presente y cinco índices de seguridad presentes.

WildFly 41 está en ejecución con Java 17 y `standalone-full.xml`.
Se desplegó el WAR de `feature/seguridad-a`; la administración informa
`StayHub.war: OK` y `server-state: running`.

Se comprobaron estas respuestas del despliegue:

- Página de inicio: 200 sobre HTTPS y cabeceras CSP/HSTS/nosniff/frame.
- Token CSRF: 200; cookie con Secure, HttpOnly y SameSite=Lax.
- Catálogo de hoteles: 200; devolvió 10 hoteles desde la base real.
- Reservas propias sin sesión: 401.
- SOAP/WSDL entrante sin vinculación de PMS: 403.
- HTTP del sitio: redirección 302 a HTTPS.

La conexión se recuperó después de los errores de tiempo de espera. Se comparó
la negociación sin GSS y TLS directo, y luego la configuración JDBC original:
las tres pudieron consultar la migración. El datasource original de WildFly
confirmó conexión válida. No se cambiaron los parámetros de negociación ni
credenciales; no hay evidencia suficiente para atribuir la recuperación a GSS,
al filtro de IP o a una causa específica de red.

| Control | Configuración guardada |
| --- | --- |
| TLS de aplicación | Elytron `StayHubTLS`, TLS 1.2/1.3; certificado de localhost y confianza en CA local para certificados PMS |
| HTTP de aplicación | GET/HEAD de `/StayHub` redirigen a `https://localhost:8443`; otras operaciones HTTP responden 426 |
| Listeners HTTP/HTTPS | 64 KiB por cuerpo, 16 KiB de cabeceras, 100 cabeceras/parámetros, 200 conexiones, 10 s de lectura/escritura y parseo, 32 streams HTTP/2 |
| Solicitudes del sitio | 32 concurrentes y hasta 32 en espera, mediante filtro Undertow |
| PostgreSQL | Pool máximo 20, espera de pool 5 s, timeout de consulta 15 s, conexión 10 s y socket 30 s |
| JMS | Límites de 10 MiB y FAIL por direcciones conocidas, colas de errores y expiración; reintentos hasta cinco y demora 10 s |
| PMS entrante | Bloqueado en Undertow y lista de hoteles autorizados vacía; huella de certificado local preparada |
| URL de emails | Propiedad del servidor `https://localhost:8443/StayHub` |
| Archivos privados | Certificados, claves cifradas, contraseñas y copias fuera de Git en `.local-security`; permisos de archivos privados 600 y directorio 700 |

La configuración quedó activa al iniciar WildFly. El WAR anterior se respaldó
antes de reemplazarlo en `.local-security/deployments/`; los backups del XML
se conservan en `.local-security/backups/`.

La confianza del certificado local para localhost se agregó al llavero de usuario
de macOS después de recibir la autorización. Este paso está completado.

## Los hoteles y el PMS

`CatalogoCanalesResource` combina hoteles de la base propia con ofertas
simuladas. No importa el catálogo de Booking/Airbnb reales. `pms-legado-mock`
es un receptor al que StayHub envía inventario; esa sincronización saliente
es diferente de la fachada `/soap/integracion-pms` que acepta peticiones entrantes.

No se encontró un registro de PMS propietario por hotel. No se dedujeron
permisos de las ofertas públicas. Para habilitar una identidad entrante se debe
vincular explícitamente su certificado con hoteles de la base. El script admite
`--hoteles-pms` para esa lista, después de comprobar el WAR protegido desplegado.
Hasta entonces la fachada entrante permanece bloqueada al activar los filtros.

## Aiven: estado y mejora pendiente

La migración y los datos de conexión están confirmados. La persona usuaria
indicó que la allowlist permite todas las IP. No se modificó esa opción en la
cuenta ni se diagnosticó como causa del fallo.

Queda facilitar el certificado CA público de Aiven (`ca.pem`) para configurar
verificación completa del servidor PostgreSQL. Actualmente se conserva el
`sslmode=require` existente, que cifra pero no valida el certificado.

El diagnóstico local resolvió DNS y abrió TCP al puerto configurado; la
solicitud inicial de negociación TLS de PostgreSQL no recibió respuesta dentro
del plazo. WildFly también devolvió `Read timed out`. No se obtuvo un error de
credenciales ni evidencia suficiente para atribuirlo a una opción concreta de
la cuenta. La documentación de Aiven explica cómo obtener los parámetros y el
certificado en [Connection information](https://aiven.io/docs/products/postgresql/howto/connect-libredb-studio)
y los [modos TLS de PostgreSQL](https://aiven.io/docs/products/postgresql/howto/list-code-samples).

## Datos de conexión confirmados por captura

La captura enviada el 6 de octubre coincide con la configuración existente:
host, puerto 28309, base defaultdb, usuario avnadmin y SSL require. No se
modificaron credenciales ni se atribuyó el fallo a una contraseña incorrecta.
Se volvió a comprobar JDBC: `Read timed out`. La prueba sin credenciales de
SSLRequest PostgreSQL no recibió respuesta; TLS directo reinició la conexión.
Esos resultados correspondieron al fallo inicial. Posteriormente JDBC y el
datasource original conectaron correctamente. La causa del fallo transitorio
no quedó identificada.

La revisión automática rechazó consultar la IP pública en api.ipify.org por
la divulgación de metadatos a un servicio externo no autorizado. Esa consulta
no se ejecutó y no se intentó un servicio alternativo. Se solicitó captura del
filtro de IP o autorización explícita para la consulta original. La conexión
se recuperó y ya no fue necesario obtener la IP pública.

## Acceso actual y pendientes funcionales

Abrir `https://localhost:8443/StayHub/index.html`. El WAR actualizado está activo.
Las reservas antiguas todavía requieren vinculación de propietario con evidencia
confiable para aparecer como propias de huéspedes. La fachada PMS entrante sigue
bloqueada hasta asignar hoteles a una identidad verificada.

Las claves y certificados generados son para desarrollo local. Los certificados
servidor/PMS tienen duración de 90 días. Para otro dominio se necesitan
certificados emitidos para ese dominio y una configuración de red apropiada.
No se instalaron certificados privados en Git ni se incorporó la CA local al
repositorio.

## Reproducción de la configuración

Desde la raíz de StayHub, con Python 3, OpenSSL y acceso a la administración local:

```sh
python3 config/configurar-seguridad-local.py \
  --wildfly-home '/ruta/a/wildfly-41.0.0.Final' \
  --openssl '/ruta/a/openssl'
```

El script guarda una copia privada antes de modificar WildFly, conserva los
certificados generados y aplica valores por CLI. No ejecuta SQL, no recarga el
servidor, no despliega el WAR y no modifica la confianza del llavero.

Las direcciones JMS usan coincidencias exactas con y sin prefijo legado; se
confirmó `jms.queue.CanalesExternosSincronizaciones` en las colas runtime.
Los patrones iniciales `#.Nombre` se corrigieron porque Artemis de WildFly 41
los rechazó. Los atributos del limitador del sitio corresponden al
[filtro request-limit oficial](https://docs.wildfly.org/wildfly-catalog/39.0.0.Final/default/featurePacks/0_org.wildfly_wildfly-galleon-pack/doc/reference/subsystem/undertow/configuration/filter/request-limit/index.html).

## Recuperación

Las copias están en `.local-security/backups/<fecha-hora>/standalone-full.xml`.
La primera corresponde a la configuración anterior a estos cambios; las
siguientes guardan el estado antes de cada aplicación. Para restaurar: detener
WildFly, guardar el XML actual, reemplazarlo por la copia elegida y arrancar
con `-c standalone-full.xml`. Mantener los permisos y archivos privados.
# Credenciales TLS fuera del XML de WildFly

La configuración Elytron referencia `${env.STAYHUB_TLS_STORE_PASSWORD}`; no
guarda la contraseña real de los archivos PKCS#12 en `standalone-full.xml`.
Antes de iniciar o recargar WildFly, exportar esa variable en el entorno de su
proceso usando el valor privado de `.local-security/secretos.json`. No copiar el
valor a scripts versionados, argumentos de línea de comandos ni propiedades del
servidor. El archivo privado se crea con permisos `0600` y está ignorado por Git.
