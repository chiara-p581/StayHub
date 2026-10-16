# Análisis de seguridad de StayHub

**Fecha:** 5 de octubre de 2026  
**Alcance:** revisión estática del repositorio `StayHub`: WAR Jakarta EE, API REST, endpoint SOAP, persistencia PostgreSQL/JPA, mensajería, clientes de integración, mock del PMS y páginas HTML/JavaScript.  
**Objetivo:** registrar qué falta antes de implementar una defensa coherente y entregar un plan verificable a quien haga la revisión de seguridad.

## Resumen ejecutivo

La aplicación ya tiene controles puntuales: registro público fuerza el rol `HUESPED`, el login rota el ID de sesión, las contraseñas se guardan con PBKDF2 y las consultas JPQL observadas usan parámetros. El filtro REST aplica una matriz básica por ruta y rol. Estos controles son un comienzo, pero no forman todavía una frontera de seguridad completa entre huésped, administrador, PMS y OTA.

Los riesgos más importantes observados son:

1. **SOAP sin autenticación ni autorización**, con operaciones que leen datos personales de reservas y cancelan reservas como un actor privilegiado.
2. **Autorización incompleta de reservas y pagos**: hay una operación de confirmación sin verificación de titular; consulta de pago por ID sin propietario; los datos de identidad y precio de la reserva directa provienen del cliente.
3. **XSS almacenado/DOM probable**: datos persistidos de hoteles, reservas y respuestas se interpolan en `innerHTML` en varias páginas.
4. **DoS por consumo ilimitado**: no se observan paginación, límites de rango/cantidad/tamaño HTTP ni rate limiting. Hay operaciones que recorren todos los días del período o listan colecciones completas. Login PBKDF2 y notificaciones son otros recursos costosos.
5. **CSRF y endurecimiento de sesión/cabeceras no definidos**: autenticación por cookie de sesión sin token anti-CSRF visible ni configuración explícita de atributos de cookie, HTTPS obligatorio o cabeceras de seguridad.
6. **Integraciones y configuración**: el endpoint OTA comparte una API key estática; la URL WSDL y URLs OTA configurables permiten riesgo de SSRF/egress si una configuración o credencial de administrador se compromete; faltan límites y políticas de confianza para XML/API externa.

La clasificación sigue categorías de OWASP Top 10:2025 y OWASP API Security Top 10:2023, en particular control de acceso, configuración, fallas de autenticación, inyección/XSS, consumo irrestricto, SSRF y consumo inseguro de APIs ([OWASP Top 10:2025](https://top10.owasp.org/2025/0x00_2025-Introduction/), [OWASP API Security Top 10](https://api-security.owasp.org/editions/2023/en/0x11-t10/)).

## Hallazgos priorizados

### CRÍTICO — SEC-01: el endpoint SOAP queda fuera de la autenticación REST

**Evidencia:** `proyecto/src/main/webapp/WEB-INF/web.xml` publica `/soap/integracion-pms`; `IntegracionPmsWebService` no implementa autenticación ni autorización.

Las operaciones `consultarReserva(reservaId)` y `listarLlegadas(hotelId, fecha)` exponen datos del huésped asociados a reservas. `cancelarReserva(reservaId)` llama a `cancelarReserva(..., ACTOR_PMS, true)`, con `actorEsAdmin=true`, por lo que confía en cualquier cliente que alcance la URL. El filtro `AutenticacionFilter` es un filtro JAX-RS REST y no protege este endpoint JAX-WS. El comentario de `web.xml` también muestra URL HTTP local, sin establecer TLS.

**Impacto:** lectura masiva o dirigida de información personal y cancelación arbitraria de reservas sin identidad PMS comprobada. `listarLlegadas` puede además recuperar todas las reservas del hotel y filtrar en memoria.

**Remediación:** exigir mTLS o credencial de servicio de corta duración para todas las operaciones SOAP; autorizar por hotel/operación y no pasar `actorEsAdmin=true` sin una identidad verificada; limitar campos de respuesta; permitir SOAP/WSDL solo desde redes necesarias; servir exclusivamente sobre HTTPS. Añadir controles de tamaño, profundidad y entidades XML en el stack del contenedor y comprobarlos con pruebas de XXE/expansión XML. Restringir también la carga máxima de resultados.

### CRÍTICO — SEC-02: reservas directas confían en email y precio enviados por el navegador

**Evidencia:** `reservas/api/ReservaResource.java` delega `POST /reservas` sin vincular huésped a la sesión. `ReservaRequest` contiene `huespedEmail` y `precioTotal`. `ServicioDeReservasImpl.validarDirecta()` solo exige email no vacío y precio no negativo; no consulta tarifa para calcular el importe ni reemplaza el email por el de la cuenta autenticada.

Una cuenta autenticada puede crear reservas a nombre de otra dirección y fijar un precio menor, incluso cero. Como las consultas de reservas propias se filtran por email, esto permite contaminación de la cuenta ajena; además, el precio manipulado pasa luego a la validación de pago como precio de referencia.

**Remediación:** construir huésped/propietario desde la identidad de sesión autenticada; separar la creación de reservas OTA del flujo de huésped; obtener precio y moneda del inventario del servidor y recalcularlos para todas las noches, tipo y cantidad; no aceptar `precioTotal` como autoridad. Aplicar reglas de estado para impedir confirmación sin pago aprobado y limitar cantidad, horizonte de fechas y frecuencia de reservas.

### CRÍTICO — SEC-03: autorización por objeto incompleta en reservas y pagos (IDOR/BOLA)

**Evidencia:** `ReservaResource.confirmar(id)` delega directo en `confirmarReserva(id)` sin verificar titular/rol. `PagoResource.consultar(id)` devuelve el pago por ID y `ServicioDePagosImpl.consultarPago()` solo busca por ID. Los POST de pago no obtienen el usuario de la sesión; la propiedad se valida únicamente al listar `/pagos/mios`.

Un huésped autenticado puede confirmar una reserva ajena conociendo su ID, consultar pagos de otras cuentas por ID y solicitar cobros asociados a IDs de reservas ajenas. `GET /pagos/mios` está restringido a sus reservas, pero no corrige los otros caminos.

**Remediación:** hacer autorización dentro del servicio de cada operación sensible, a partir de usuario autenticado y propietario persistido; resolver reserva y dueño en una sola consulta/operación. Restringir consultar, confirmar, cancelar, modificar y pagar a titular o a rol administrativo explícito. Tratar inexistente y ajeno de forma que no permita enumeración. Añadir pruebas negativas para cada endpoint/ID y rol.

### ALTO — SEC-04: XSS en la interfaz de usuario al renderizar datos con `innerHTML`

**Evidencia:** `hotel-details.html` escapa algunos valores, pero `settings.html`, `hotel-management.html`, `inventory.html`, `channels-sync.html`, `alerts.html`, `payments.html` y `checkout.html` interpolan valores de API en cadenas HTML. Ejemplos claros: `settings.html` inserta `e.message`; `hotel-management.html` inserta `h.nombre`, `t.nombre` y datos de habitación; `checkout.html` inserta `i.hotelNombre`. Los valores de catálogo se guardan en base de datos y parte del contenido puede venir de OTAs.

**Impacto:** un nombre/descripción/valor controlado por un usuario, administrador comprometido o proveedor externo puede ejecutar JavaScript en el origen del sitio y robar/abusar de la sesión. La cookie HttpOnly reduce lectura directa de la cookie, pero no impide acciones autenticadas ejecutadas desde el navegador.

**Remediación:** preferir `textContent`, creación de nodos y asignación de atributos seguros; no interpolar texto no confiable en etiquetas, atributos, URLs o clases. Si se necesita HTML enriquecido, sanear con una biblioteca mantenida y política estricta. Añadir CSP como defensa adicional después de retirar scripts inline o migrarlos a nonce/hash. Cubrir datos maliciosos almacenados y respuestas externas con pruebas.

### ALTO — SEC-05: no hay límites suficientes para resistir DoS por consumo de recursos

**Evidencia:** no se encontró paginación ni límites de tamaño en solicitudes JSON en recursos REST; no hay rate limit por IP/cuenta. Los GET de hoteles e inventario retornan listas sin máximo visible; reservas `/reservas?hotelId=...` y el flujo SOAP pueden cargar listas completas. `ServicioDeInventarioYTarifasImpl.noches()` crea una clave por noche y acepta cualquier período con `hasta > desde`, y los GET públicos aceptan períodos sin máximo de duración. Los lotes de pago no tienen máximo de IDs. Registro/login no limitan longitud de contraseña; PBKDF2 se ejecuta con 65.536 iteraciones por intento. Notificaciones pueden disparar email/SMS/push sin cuota por destinatario.

**Impacto:** agotamiento de CPU, memoria, conexiones, base de datos o coste de proveedores mediante solicitudes sencillas repetidas o rangos/listas enormes. OWASP clasifica este escenario como consumo irrestricto de recursos y DoS a nivel API ([OWASP API4:2023](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)).

**Remediación:** establecer límites globales y por endpoint (bytes de request, profundidad JSON, máximo de IDs, fechas futuras/máximo de noches, número de resultados y tamaño de página); paginar en base de datos; límites de concurrencia/colas y timeouts de servidor; rate limiting y cuotas para login/registro/reserva/pagos/notificaciones/webhooks; backpressure y límites para JMS; limitar longitud de contraseña y campos antes de procesarlos. Añadir alertas y pruebas de carga con umbrales definidos.

### ALTO — SEC-06: CSRF posible y configuración de cookies/HTTPS/cabeceras ausente

**Evidencia:** el navegador autentica con `JSESSIONID` y `fetch(... credentials: "same-origin")` en `assets/js/api.js`. No se observa token CSRF, validación `Origin`/`Referer`, ni configuración de SameSite/Secure/HttpOnly en `web.xml`. Tampoco se observan cabeceras CSP, HSTS, `X-Content-Type-Options`, `frame-ancestors`/protección de framing, `Referrer-Policy` o `Permissions-Policy` en la aplicación.

**Impacto:** un sitio externo podría inducir acciones de sesión si el navegador adjunta la cookie; exposición de sesión en conexiones HTTP y mayor impacto de XSS/ clickjacking si el proxy/servidor no agrega defensas. El comportamiento por defecto de WildFly/proxy no se verificó, por eso este punto debe comprobarse también en el despliegue.

**Remediación:** configurar cookie de sesión `Secure`, `HttpOnly`, `SameSite=Lax` o `Strict` según flujo; HTTPS obligatorio con HSTS en el borde; implementar token CSRF para mutaciones y validar `Origin` como defensa complementaria. Definir CSP compatible con el sitio, `frame-ancestors`, `X-Content-Type-Options: nosniff`, `Referrer-Policy` y `Permissions-Policy` en proxy o aplicación; documentar qué capa es responsable y verificar las respuestas desplegadas.

### ALTO — SEC-07: API key compartida para webhooks OTA sin identidad por canal ni anti-replay

**Evidencia:** `AutenticacionFilter` valida un único `X-StayHub-Channel-Key` configurado por propiedad/entorno para todas las rutas OTA. La comparación en tiempo constante es positiva, pero no hay separación de credenciales por proveedor, firma del cuerpo, timestamp, nonce, expiración ni rotación visible. Los webhook actualizan/cancelan reservas externas.

**Impacto:** compromiso o filtración de esa única credencial habilita acciones para cualquier canal; un mensaje capturado puede repetirse. La cuenta `ADMIN` también pasa directamente por estas rutas sin clave.

**Remediación:** credenciales distintas por canal, almacenadas en secret manager y rotables; firmas HMAC sobre cuerpo canónico + timestamp/nonce con ventana acotada, control anti-replay/idempotencia y límites de cuerpo/rate. Para canal desde sesión de administrador, aplicar una ruta y permiso administrativos ordinarios, no mezclarlo con la autenticación de webhook.

### ALTO — SEC-08: SSRF/egress y confianza incompleta en integraciones configurables

**Evidencia:** `ConfiguracionCanales.urlOta()` y `wsdlPms()` aceptan URLs de propiedades/variables; `PmsLegacyClientImpl` crea un servicio JAX-WS desde esa URL, y `OtaRestClientImpl` hace llamadas HTTP a la URL configurada. No se ven allowlists de host/esquema, bloqueo de IP privada/link-local, validación de redirecciones ni política de salida. Configuración incorrecta o control de una propiedad podría alcanzar servicios internos. Las respuestas externas se consumen con controles básicos de estado, no hay límites explícitos de tamaño, esquema o campos en estos clientes.

**Remediación:** permitir solo hosts/esquemas de integración aprobados; bloquear loopback, redes privadas, link-local y metadata cloud; restringir egress en red; desactivar/validar redirecciones; límite de bytes y tiempo total, TLS con verificación de certificado, validación estricta de respuesta y circuit breaker. Asegurar que URLs y tokens no se incluyan en errores/logs.

### MEDIO — SEC-09: autenticación vulnerable a abuso y recuperación de cuenta no implementada

**Evidencia:** `ServicioDeUsuariosImpl` acepta contraseñas de al menos seis caracteres, sin máximo o política de credenciales comprometidas; el login público no tiene throttling, bloqueo progresivo ni desafío por abuso. `PasswordHasher` usa PBKDF2-HMAC-SHA256 con salt aleatoria y comparación constante, controles positivos; su coste hace que el endpoint de login sea también recurso consumible. `recover-password.html` confirma expresamente que la recuperación no existe.

**Remediación:** política de contraseñas actualizada (mínimo razonable, máximo alto pero acotado, prevención de contraseñas comunes/filtradas), límites y demoras progresivas en login/registro, respuesta uniforme y telemetría contra credential stuffing. Implementar recuperación con token aleatorio de un solo uso, corto vencimiento, hash persistido, invalidación de sesiones y respuesta que no enumere emails. Revisar el costo PBKDF2 según capacidad y política de migración de hashes.

### MEDIO — SEC-10: redirección abierta al volver del login

**Evidencia:** `login.html` lee `next` de la query y ejecuta `location.href = decodeURIComponent(next)` sin validar que sea una ruta local. `URLSearchParams` ya decodifica el valor; la segunda decodificación facilita dobles codificaciones.

**Impacto:** enlace de phishing con apariencia de StayHub que deriva a un sitio externo después de autenticar.

**Remediación:** permitir solo nombres de páginas locales en una lista cerrada o aceptar rutas relativas que no empiecen con `//`, barra invertida ni esquema; parsear como URL contra el origen actual y comprobar mismo origen. Eliminar la doble decodificación.

### MEDIO — SEC-11: cadena de suministro y contenido de terceros sin endurecimiento visible

**Evidencia:** `pom.xml` fija Jakarta EE API como `provided` y dependencias de pruebas, pero no configura auditoría/SBOM/dependabot/OWASP Dependency-Check ni política de actualización del runtime WildFly/JDK. Las páginas cargan Tailwind desde `cdn.tailwindcss.com`, Google Fonts y algunas imágenes remotas; no se ven SRI ni CSP. El runtime WildFly, proxy, sistema operativo, PostgreSQL y broker no están incluidos en esta revisión.

**Remediación:** inventariar versiones reales del runtime y módulos, generar SBOM, escanear dependencias e imágenes, fijar actualizaciones y revisión de CVEs; autoalojar recursos de ejecución críticos cuando sea posible o fijar versiones/integridad/CSP para terceros. Escanear artefactos de build y revisar secretos/configuración antes de publicar.

### MEDIO — SEC-12: persistencia y reglas de negocio requieren endurecimiento

**Evidencia:** `persistence.xml` establece `hibernate.hbm2ddl.auto=update`, que permite cambios del esquema desde el despliegue de aplicación; no es una estrategia controlada de migraciones. La prevención de doble pago es un `verificarNoPagada()` separado del cobro/guardado y no se ve restricción única por reserva aprobada ni clave idempotente de operación. Hay ventanas de carrera ante pagos concurrentes y estados de reserva/pago no modelan claramente reembolsos.

**Remediación:** usar migraciones versionadas fuera del arranque de producción y usuario DB con privilegio mínimo; restricciones únicas/operaciones atómicas para pagos; idempotencia ligada a usuario/checkout; verificar titular, estado y precio dentro de transacción; definir reconciliación, timeouts y compensación de pagos externos. Separar cuentas/roles PostgreSQL por aplicación y restringir red/backup.

### MEDIO — SEC-13: auditoría de seguridad y errores no centralizados

**Evidencia:** no se identifica un registro estructurado de login fallido/exitoso, cambios de perfil, cambios de rol, mutaciones administrativas, webhooks, pagos, bloqueos ni decisiones de autorización. Algunos mappers capturan causas para mensajes; la política global de logging/redacción/retención no está documentada.

**Remediación:** eventos estructurados con actor, acción, resultado, objeto, request/correlation ID y origen, sin passwords, tokens, PAN/CVV ni datos personales innecesarios. Alertas sobre fuerza bruta, errores anormales y actividad de administración. Evitar devolver excepciones internas, referencias de proveedor o stack traces a clientes; preservar detalle solo en logs protegidos y saneados.

## Revisión específica por superficie

| Superficie | Estado observado | Qué falta cerrar |
|---|---|---|
| Autenticación REST | Filtro por rol y sesión HTTP; registro fuerza `HUESPED`; ID de sesión cambia en login. | CSRF, sesión/cookie/TLS explícitos, rate limit, expiración y revocación, matriz de permisos central y pruebas por endpoint. |
| Usuarios | PBKDF2 con salt individual y `MessageDigest.isEqual`; perfil no acepta rol. | Reglas de email/campos/longitud, límite de contraseña, antifuerza bruta, recuperación, confirmación de email y auditoría. |
| Hoteles/catálogo | GET público; escrituras protegidas por admin en filtro. Consultas JPA observadas parametrizadas. | Autorización de datos inactivos, límites/paginación, validación de texto/longitud y salida HTML segura. |
| Inventario/tarifas | Validación de período y carga; consultas GET públicas. Carga solo admin. | Máximo de noches/rango/unidades/precio, paginación, cuotas y cálculo de precio seguro desde servidor. |
| Reservas/carrito | Crear requiere sesión; cancelación/modificación comparan email salvo admin. | Crear desde identidad de sesión; confirmar verifica dueño; precio y huésped no confiables; límites de carrito/retención/fecha y estado; idempotencia. |
| Pagos | Verifica monto contra reserva y evita pago aprobado previo en flujo normal; pasarela configurada. | Verificación de propietario para crear/consultar; concurrencia/idempotencia; no autorizar con datos de frontend; firma/verificación de webhook de pasarela; PCI/alcance de PAN/CVV. |
| Canales externos/OTA | Admin para sincronizaciones; timeout de conexión/lectura; API key compartida en webhook. | Identidad por proveedor, anti-replay, cuotas, límites de entrada, allowlist/egress, validación robusta y control de carga JMS. |
| PMS SOAP | Contrato explícito y traducción de errores, pero sin autenticación de endpoint. | Autenticación fuerte, autorización por hotel, TLS, control de XML/tamaño, limitación de operaciones/datos y acceso de red. |
| Notificaciones | Operación exige ADMIN por filtro. | Cuota/rate limit, allowlist de destinatarios según negocio, prevenir abuso/coste, límites de mensaje y auditoría sin PII. |
| Overbooking/demo | Operaciones administrativas por filtro. | Proteger APIs independientemente de UI, límites de carga/ejecución e idempotencia del demo. Evitar usar producción para datos demo. |
| Frontend | Las rutas de administrador/usuario y controles de formulario se ocultan en UI; almacenamiento local contiene carrito/cache y perfil público. | UI no es autorización. Reparar sinks XSS; validar redirección `next`; impedir scripts de terceros arbitrarios; no confiar en carrito, perfil ni precios del navegador. |
| PMS mock | Servicio SOAP de desarrollo disponible en módulo aparte. | Asegurar que el mock no se despliegue en producción; restringir endpoints y datos de prueba. |

## Inyección SQL: resultado de esta revisión

No se encontró concatenación de entrada del usuario en SQL nativo. Las consultas JPQL revisadas (`UsuarioRepositoryJpa`, `ReservaRepositoryJpa`, `PagoRepositoryJpa`) usan parámetros nombrados. Hay una consulta SQL nativa en `InventarioDiarioRepositoryJpa` para bloqueo/actualización de inventario: debe quedar en la revisión de detalle, comprobar que todos los valores externos van por parámetros y que nunca se construye SQL dinámicamente. Esto reduce el riesgo visible de SQL injection, pero no sustituye la validación ni una revisión dinámica con payloads. En el frontend el riesgo inmediato de inyección es XSS vía `innerHTML`, no SQL.

## Controles existentes que conviene conservar

- Registro público reconstruye la solicitud con rol `HUESPED`, sin confiar en el rol enviado por cliente.
- Login invoca `changeSessionId()` antes de guardar identidad en la sesión.
- Hash de contraseña con salt aleatoria y comparación en tiempo constante.
- Consultas JPQL observadas parametrizadas.
- Clientes REST y SOAP salientes definen timeouts de conexión/lectura.
- Varias reglas de autorización de reservas se implementan en el servicio y no solo en la interfaz.

Estos puntos no cubren las brechas listadas y deben tener pruebas de regresión al ampliar la seguridad.

## Orden recomendado de implementación

### P0 — bloquear exposición/explotación directa

1. Cerrar o autenticar SOAP; eliminar la confianza implícita `actorEsAdmin=true` y limitar campos/resultados.
2. Aplicar autorización por titular en cada endpoint de reservas y pagos; asegurar que la identidad/propietario viene de la sesión y no del request.
3. Calcular precio de reserva únicamente con inventario/tarifa del servidor; impedir confirmación fuera del flujo de pago autorizado.
4. Corregir todos los sinks `innerHTML` con datos remotos/no confiables.
5. Agregar límites iniciales de tamaño, período, resultados y rate limit a login, reservas, inventario, notificaciones y webhooks.

### P1 — defensa de sesión, integraciones y persistencia

6. Token CSRF + atributos seguros de cookie + HTTPS/HSTS + cabeceras.
7. Separar credenciales OTA, firmar payloads, anti-replay, restricciones de egress/SSRF.
8. Asegurar operaciones de pago con idempotencia/transacción/restricciones DB y manejo firmado del resultado de pasarela.
9. Migraciones DB, privilegios mínimos y política de backups/secretos.

### P2 — prevención, observabilidad y cadena de suministro

10. Protección de cuentas y recuperación de contraseña segura.
11. Logs y alertas de seguridad con redacción de datos sensibles.
12. SBOM, escaneo continuo de dependencias/runtime/imagen, CSP y pruebas de seguridad automatizadas.

## Criterios de aceptación para la implementación

- Matriz de acceso publicada y testeada para cada método/ruta/rol, incluido SOAP y webhook; huéspedes no leen ni mutan objetos ajenos.
- Ningún campo de propietario, rol, precio o estado crítico se acepta como autoridad del cliente.
- Pruebas de payloads XSS almacenados reflejan texto inerte; CSP bloquea scripts no aprobados.
- Pruebas de CSRF, cookies y HTTPS contra despliegue real; las respuestas contienen cabeceras esperadas.
- Solicitudes sobredimensionadas, rangos extremos, lotes grandes y abuso de login/notificación responden con límites previsibles sin agotar el servicio.
- Integraciones rechazan hosts no permitidos, certificados inválidos, respuestas demasiado grandes, XML inseguro y mensajes OTA repetidos.
- Pagos repetidos/concurrentes no producen cobros duplicados; operaciones ajenas fallan sin revelar datos.
- Escaneo de dependencias/runtime y pruebas dinámicas dejan evidencia/versiones reproducibles.

## Límites de esta revisión

Este informe deriva del código fuente visible y configuración incluida en el repo. No se ejecutó la aplicación ni se hicieron pruebas DAST, fuzzing, carga, análisis de infraestructura o inspección del WildFly/proxy/PostgreSQL/broker desplegados. Tampoco se verificaron credenciales reales o política de secretos externa. Antes de declarar un control ausente en producción, verificar si lo agrega una capa externa; después registrar esa responsabilidad y probar la respuesta efectiva. La clasificación de severidad es inicial y debe ajustarse con exposición real, datos, modelo de amenazas y entorno.
