# Seguridad — KAN-53 a KAN-56

## KAN-53 — Integraciones salientes

- OTA y PMS aceptan únicamente HTTPS hacia hosts incluidos en
  `stayhub.canales.hosts-permitidos`.
- Se rechazan loopback, redes privadas/link-local, resoluciones DNS privadas,
  credenciales embebidas, fragmentos, barras inversas, rutas que cambian al
  normalizarse y URLs mayores a 2.048 caracteres.
- Los timeouts quedan acotados a 3 s de conexión y 10 s de lectura, y los
  clientes no siguen redirecciones.
- El token OTA es obligatorio, tiene longitud máxima y no admite caracteres de
  control que permitan inyectar cabeceras.
- El endpoint efectivo del PMS se valida nuevamente y debe compartir origen
  con el WSDL; se sobrescribe cualquier endpoint anunciado por el WSDL.
- La respuesta PMS se rechaza si es nula o contiene un mensaje excesivo o con
  controles inválidos.

Configuración mínima de producción:

```properties
stayhub.canales.hosts-permitidos=ota.ejemplo.com,pms.ejemplo.com
stayhub.ota.booking.url=https://ota.ejemplo.com/api
stayhub.ota.booking.token=SECRETO_EXTERNO
stayhub.pms.wsdl=https://pms.ejemplo.com/PmsLegacyService?wsdl
stayhub.pms.endpoint=https://pms.ejemplo.com/PmsLegacyService
```

La restricción de salida debe repetirse en firewall/proxy para que una
re-resolución DNS posterior no pueda eludir la política de la aplicación.

## KAN-54 — Web Storage

- `localStorage` conserva solamente la preferencia visual.
- Las respuestas de API se cachean únicamente en memoria y se eliminan caches
  persistentes de versiones anteriores.
- El carrito pasó de `localStorage` a `sessionStorage`, por lo que no sobrevive
  al cierre de la pestaña.
- El perfil guardado excluye email y cualquier credencial. La identidad y la
  autorización reales continúan en la sesión HttpOnly del servidor.
- El frontend dejó de copiar nombre y email del huésped a la creación de la
  reserva; el servidor los obtiene desde la sesión autenticada.
- Logout elimina perfil, carrito e identificadores temporales de pago.

## KAN-55 — CSP

- `style-src` ya no contiene `'unsafe-inline'`.
- Los estilos estáticos heredados están autorizados por hashes SHA-256 y
  `'unsafe-hashes'`; un estilo inyectado diferente no coincide con la política.
- Scripts, fuentes y conexiones se limitan al mismo origen. Frames, objetos y
  workers quedan deshabilitados; imágenes sólo admiten mismo origen y `data:`.
- No se cargan recursos visuales ni scripts desde hosts externos.
- Una prueba recalcula los hashes de HTML/JavaScript y falla si se agrega un
  estilo inline sin actualizar deliberadamente la CSP.

## KAN-56 — Contraseñas TLS de WildFly

- `configurar-seguridad-local.py` ya no concatena la contraseña real en las
  operaciones Elytron.
- `standalone-full.xml` guarda `${env.STAYHUB_TLS_STORE_PASSWORD}` y WildFly
  obtiene el secreto del entorno de su propio proceso.
- El secreto local continúa en `.local-security/secretos.json`, ignorado por
  Git y con permisos `0600`.
- El entorno debe configurarse antes de iniciar o recargar WildFly. Si la
  variable no está disponible, los almacenes TLS fallan cerrados.

## Evidencia automatizada

```sh
cd proyecto && mvn test
cd ui-security && npm test
python3 -m py_compile ../../config/configurar-seguridad-local.py
```

Las pruebas cubren URLs normalizables/excesivas, cambio de origen del PMS,
credenciales OTA inválidas, ausencia de `unsafe-inline`, hashes CSP completos,
limpieza/minimización de Web Storage y ausencia de contraseña literal en el
configurador.
