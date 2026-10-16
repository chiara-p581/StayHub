# Implementación de seguridad A — SEC-01 a SEC-06

Rama: `feature/seguridad-a`. Base incorporada: `development`, commit `30a13f4`.

Actualización del 6 de octubre: migración verificada en PostgreSQL, configuración
local activa en WildFly 41 y WAR desplegado con estado OK. Ver
[estado y comprobaciones locales](CONFIGURACION_LOCAL_SEGURIDAD.md).

Este documento acompaña al diagnóstico original en `ANALISIS_SEGURIDAD.md`.
Incluye pruebas automatizadas y comprobaciones HTTP básicas del despliegue local;
no acredita un análisis DAST completo, pruebas de carga ni integración con un
PMS o proveedor de pagos/notificaciones real.

## Cobertura para revisión

| Ticket | Implementación | Evidencia principal |
| --- | --- | --- |
| SEC-01 | Todo `/soap/*`, incluido WSDL, requiere certificado cliente TLS vigente y huella configurada. El certificado se vincula a hoteles permitidos; los servicios verifican ese alcance. Se eliminó el parámetro que simulaba un administrador. Respuesta PMS sin email, importe ni referencia externa. XML sin DTD, entidades externas o XInclude y con complejidad acotada. | `SeguridadHttpFilter`, `IdentidadActual`, `IntegracionPmsWebService`, `IntegracionPmsMapper`, `CuerpoSeguro` |
| SEC-02 | Reservas directas solo para huéspedes autenticados. Propietario persistido por ID y perfil recuperado del servidor; nombre/email/precio/moneda del cliente no son autoridad. Tarifa calculada con todas las noches y cantidad, sin mezclar monedas. Carrito aplica la misma identidad y el servicio recalcula al confirmar. | `Reserva.usuarioId`, `ServicioDeReservasImpl`, `CarritoDeReserva`, `InventarioTarifasMapper` |
| SEC-03 | Consultar, cancelar, modificar, confirmar y pagar verifican propietario o permiso administrativo/PMS dentro del servicio. Lotes autorizan todos los IDs antes del cobro. Confirmación exige pago aprobado; operaciones de pago bloquean la reserva y verifican estado/vencimiento. IDs de reservas ajenas/inexistentes devuelven el mismo 404. | `IdentidadActual`, servicios de reservas/pagos, repositorios con bloqueo pesimista |
| SEC-04 | Texto escapado y todas las escrituras de `innerHTML` pasan por DOMPurify local. Scripts de las 18 páginas extraídos a archivos; eventos HTML retirados. Tailwind compilado localmente. CSP restringe scripts al origen y bloquea objetos, frames y formularios externos. | `assets/js/dom-seguro.js`, `assets/js/vendor`, `assets/js/pages`, `assets/css/tailwind.css`, `ui-security` |
| SEC-05 | Límites de bytes, complejidad, campos, rangos, lotes, resultados, cuotas y concurrencia. Paginación en la consulta a BD; llegadas SOAP filtradas en BD. Reservas pendientes con cuota y expiración; notificaciones con cuota de destinatario. Configuraciones de servidor y JMS entregadas como plantillas. | `Limites`, `CuerpoSeguro`, `LimitadorSolicitudes`, `SeguridadHttpFilter`, repositorios, `config/seguridad-a-*` |
| SEC-06 | CSRF de sesión en mutaciones REST, incluido login/registro/logout; comprobación adicional de Origin. Frontend obtiene y envía token automáticamente. Sesión renovada al login y cerrada antes de limpiar la interfaz. Cookie HttpOnly/Secure/SameSite=Lax, 15 minutos y sin sesión en URL. HTTPS obligatorio y cabeceras defensivas. | `Csrf`, `SeguridadResource`, `ConfiguracionSesion`, `UsuarioResource`, `api.js`, `web.xml` |

## Límites iniciales

| Recurso | Límite |
| --- | --- |
| Cuerpo JSON/XML | 64 KiB, también sin Content-Length; contenido comprimido rechazado |
| URI / cabeceras en aplicación | 2.048 caracteres / 16.384 caracteres acumulados; el listener controla bytes |
| JSON | profundidad 20; números de hasta 19 dígitos de precisión, escala 0–4 y representación de 32 caracteres |
| XML SOAP | profundidad 32, 5.000 elementos, sin entidades externas |
| Solicitudes REST/SOAP activas | 32 por instancia; exceso responde 503 |
| Frecuencia global | 180 por IP/minuto |
| Login | 10 por IP/minuto y por email normalizado/minuto |
| Registro | 5 por IP/minuto |
| Notificaciones/sincronización | 10 por IP y usuario/minuto; email 5 por destinatario/minuto |
| Otras operaciones | 60 por IP y usuario/minuto; exceso responde 429 y Retry-After |
| Reserva | máximo 10 habitaciones, 366 noches, checkout hasta 2 años; creación desde hoy |
| Pendientes por huésped | 10; caducan a los 30 minutos; limpieza cada 5 minutos, hasta 100 por ejecución |
| Pago por lote | 20 reservas distintas |
| Listados | página máxima 100; offset 0–10.000; inventario máximo 5.000 filas por consulta |
| Contraseña / campos | contraseña hasta 1.024 caracteres antes de PBKDF2; demás campos según su dominio |

La tabla del limitador tiene máximo 10.000 claves, expira entradas y rechaza cuando
se llena. Las cuotas son por instancia: un despliegue con varios nodos necesita
un limitador compartido en el borde. Se usa la IP de conexión; no se confía en
`X-Forwarded-For` recibido directamente del cliente.

Listados REST de hoteles, tipos, habitaciones, reservas y pagos admiten `offset` y
`limite`. Colecciones anidadas y listados sin parámetros quedan acotados a 100;
para catálogos mayores se deben consumir las colecciones paginadas. Una búsqueda
de inventario de más de 5.000 filas se rechaza: no entrega disponibilidad parcial.

## Cambios de comportamiento

- Un ADMIN puede gestionar reservas, pero la compra directa requiere HUESPED.
- Una reserva confirmada o con pago aprobado no se puede modificar mediante el
  endpoint actual. Se devuelve 409 hasta implementar un flujo de ajuste de pago.
- Cambiar el email del perfil no cambia la propiedad de las reservas.
- Reservas antiguas y OTA sin `usuario_id` no aparecen como propias de un huésped.
  ADMIN conserva acceso; PMS solo dentro de sus hoteles autorizados.
- La excepción CSRF del webhook OTA exige la API key existente validada en tiempo
  constante. Su sustitución por identidad por canal y protección anti-replay sigue
  perteneciendo a SEC-07. SOAP usa certificado y no autentica mediante cookies.

## Despliegue necesario

### 1. PostgreSQL

Respaldar y revisar el esquema; aplicar `config/seguridad-a-schema.sql` antes del
WAR. Incluye columna, FK e índices. No deduce propietarios por email: el sistema
anterior permitía que ese dato lo eligiera el cliente. Preparar un mapa de reservas
y usuarios validado con evidencia confiable y asignar solamente filas aún sin dueño.
La persona usuaria ejecutó el batch en Aiven; se verificaron columna, FK y los
cinco índices mediante consultas de solo lectura.

### 2. HTTPS y certificado PMS

Configurar TLS con una cadena válida en Elytron y un trust manager que valide la
CA de los certificados PMS. Sobre un listener compartido con huéspedes, solicitar
certificado con `want-client-auth=true`: el filtro exige certificado para SOAP.
Un listener dedicado al PMS puede usar `need-client-auth=true`, junto con restricción
de red. Exigirlo en el listener público también obligaría al huésped a presentar
certificado. La aplicación recibe el atributo TLS del contenedor y nunca acepta
un certificado enviado en un header. Estos atributos se documentan en la
[referencia oficial de Elytron](https://docs.wildfly.org/39/feature-pack/doc/reference/subsystem/elytron/server-ssl-context/index.html).

Configurar sin guardar claves privadas en Git:

| Propiedad Java / variable de entorno | Contenido |
| --- | --- |
| `stayhub.pms.certificado-sha256` / `STAYHUB_PMS_CERTIFICADO_SHA256` | SHA-256 hexadecimal del certificado cliente autorizado; admite `:` |
| `stayhub.pms.hoteles` / `STAYHUB_PMS_HOTELES` | IDs positivos separados por coma; hasta 100 hoteles |

Actualmente una huella define una identidad PMS. Varios PMS independientes
requieren ampliar el registro de certificados y alcances antes de compartir acceso.
Sin estas propiedades o certificado válido, SOAP falla cerrado. Si un proxy termina
TLS, no basta agregar headers: conservar mTLS hasta el contenedor o implementar una
identidad interna verificada específicamente para esa arquitectura.

Solo para desarrollo en la misma máquina se puede activar
`-Dstayhub.seguridad.http-local=true` o `STAYHUB_SEGURIDAD_HTTP_LOCAL=true`.
Permite HTTP únicamente por loopback hacia localhost y desactiva Secure en la
cookie; no debe activarse en producción. SOAP continúa exigiendo TLS y certificado.

### 3. Recursos de servidor, broker y borde

Revisar y aplicar `config/seguridad-a-undertow.cli` al listener real. La plantilla
acota conexiones, cabeceras, parámetros, bytes, concurrencia HTTP/2 y esperas de
lectura/escritura. `read-timeout` controla inactividad entre lecturas; no sustituye
un plazo total de subida. Configurar también ese plazo o un caudal mínimo en el
proxy para conexiones que envían bytes lentamente. Los atributos se contrastaron
con la [referencia de Undertow](https://docs.wildfly.org/wildfly-catalog/39.0.0.Final/default/featurePacks/0_org.wildfly_wildfly-galleon-pack/doc/reference/subsystem/undertow/server/https-listener/index.html).

Aplicar `config/notificaciones-reintentos.cli` y luego revisar
`config/seguridad-a-jms.cli`: máximo 10 MiB por dirección, política FAIL y cinco
intentos. Comprobar que los patrones coinciden con las direcciones runtime y que
la cola de errores existe. Limitar además pool de conexiones, consumidores, disco
del broker y tiempos de proveedores según capacidad medida. La política FAIL y
los límites por dirección están en la [referencia de mensajería WildFly](https://docs.wildfly.org/wildfly-catalog/39.0.0.Final/default/featurePacks/0_org.wildfly_wildfly-galleon-pack/doc/reference/subsystem/messaging-activemq/server/address-setting/index.html).

Las plantillas se adaptaron y aplicaron a la configuración persistente del
WildFly 41 local y se activaron al arrancarlo. En otros servidores hay que adaptar
nombres y contrastar atributos con la versión instalada. La aplicación protege consumo por
solicitud; la mitigación de tráfico volumétrico necesita controles de red/borde.

## Validación y reproducción

Java 17, Jakarta EE 10. Desde `proyecto`:

```sh
mvn package
```

Resultado local: 71 pruebas Java sin fallos. Cubren autorización real por sesión,
hotel PMS, precios confiables, lotes ajenos, confirmación sin pago, cuotas, CSRF,
origen, HTTPS, certificado, límites y rechazo de XML externo/profundidad. Mockito
usa `mock-maker-subclass` para evitar depender del attach de agentes JVM.

Desde `proyecto/ui-security`:

```sh
npm ci
npm run build
npm test
```

Resultado local: cuatro pruebas de interfaz sin fallos. Comprueban scripts locales
sin handlers inline, todas las escrituras HTML saneadas, payloads maliciosos y
login/logout con CSRF. Los assets compilados y la licencia DOMPurify se incluyen
en el repositorio; el WAR no descarga scripts ni compila Tailwind en el navegador.

Se comprobó el arranque de JPA y la consulta del catálogo en PostgreSQL real,
HTTPS/cabeceras, token CSRF/cookie y rechazos de sesión/SOAP en WildFly. No se
ejecutó DAST completo, carga, navegación completa ni flujos reales de pago,
SMTP o PMS. Antes de dar por cerrado
el despliegue, verificar cookie/cabeceras efectivas, certificado PMS válido/ajeno,
CSRF, persistencia de propietarios, consultas paginadas y presión de servidor/colas.
Los tickets SEC-07 a SEC-13 permanecen fuera de esta implementación.
