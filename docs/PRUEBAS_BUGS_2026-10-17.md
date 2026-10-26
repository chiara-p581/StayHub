# Verificación de experiencia de huésped — 17/10/2026

Rama: `bugs`. Aplicación compilada y actualizada en WildFly local.

## Verificado en Safari

- Inicio de sesión de huésped (credenciales ingresadas por la usuaria).
- Consulta de cuenta, reservas y pagos; guardado de perfil sin modificar datos.
- Búsqueda por texto, filtro Booking y conservación de fechas al abrir el hotel.
- Tipo sin tarifa: se informa y se impide agregar al carrito.
- Habitación STANDARD, hotel StayHub Palermo, del 20 al 22 de octubre de 2026: ARS 184.000.
- Teléfono obligatorio; agregado al carrito y creación de reserva #13.
- Tarjeta ficticia inválida rechazada en el navegador.
- Pago simulado #9 aprobado para la reserva #13 con tarjeta de prueba 4111…1111. No se enviaron datos de tarjeta al servidor.
- Reserva visible en detalle e historial; pago aprobado visible en My payments; carrito vacío después del pago.
- Cierre de sesión y navegación pública.
- Ayuda: búsqueda, respuesta desplegable, contacto informativo; página About us.
- Filtro Booking muestra tres resultados y contador de tres propiedades.
- Sesión vencida: redirección al login, conservando la página de retorno.

## Correcciones

- Permisos EJB explícitos en listarMias, consultarParaPago y listados paginados/de llegadas. Se mantienen los controles de sesión y propietario.
- Creador JSON-B explícito para PagoRequest y PagoLoteRequest: había constructores ambiguos que impedían procesar pagos con HTTP 400.
- Limpieza de sesión y token CSRF al recibir HTTP 401; retorno al login en solicitudes protegidas.
- Descarte de respuestas de tarifa antiguas cuando cambia la selección.
- Error visible y botón recuperable cuando el carrito está lleno.
- Reservas pendientes identificadas como Pending payment.
- Ayuda con búsqueda, categorías, acordeones accesibles y contenido acorde al alcance real de la demo.
- Contador de hoteles según los resultados filtrados.
- URL predeterminada de los enlaces de correo actualizada a HTTPS, acorde al servidor local.

## Pruebas automáticas

- Maven: 99 pruebas, sin fallos ni errores; incluye JSON-B real con la misma versión de Yasson que WildFly y controles de autorización de reservas.
- Interfaz: 22 pruebas, sin fallos. Incluyen respuestas fuera de orden de tarifas, carrito lleno, estado pendiente, reintento de pago, pago de varias reservas, ayuda y filtros.
- Compilación WAR y despliegue correctos; git diff --check correcto.

## Pendientes y límites

- Correo: el consumidor de notificaciones registra `Falta configurar stayhub.mail.remitente`. Se necesitan remitente y credenciales SMTP válidos; no se inventaron ni se guardaron credenciales en el repositorio. No se verificó recepción de confirmaciones ni recuperación de contraseña por correo.
- Registro y cambio/restablecimiento de contraseñas no se completaron manualmente en Safari. El restablecimiento cuenta con pruebas automáticas de sus páginas y backend, pero falta comprobar la entrega real del enlace.
- ADMIN: probado según el apartado siguiente.
- La demo no tiene interfaz de huésped para cancelar/modificar reservas ni guardar medios de pago. No se agregaron esas funcionalidades nuevas; la ayuda informa el alcance real.
- Pago individual y checkout de múltiples reservas verificados automáticamente; en Safari se completó una compra de una reserva con pasarela simulada.
- La reserva #13 y el pago simulado #9 se conservaron como evidencia. No hubo cobro real.
- Los cambios quedan sin commit ni push.

## Corrección adicional: ingreso de cuentas anteriores

- El intento de ADMIN de las 19:39 fallaba en la validación de longitud de autenticar, antes de consultar el hash: el mínimo de 12 caracteres para contraseñas nuevas se aplicaba también al login.
- El login ahora verifica contraseñas existentes no vacías hasta 128 caracteres contra su hash; el registro y el cambio de contraseña mantienen el mínimo de 12.
- UsuarioException se declara como excepción de aplicación EJB para que los rechazos de credenciales lleguen como HTTP 401 al mapper y no se conviertan en HTTP 500.
- Cuatro pruebas adicionales: contraseña anterior correcta, incorrecta, mínimo de registro conservado y excepción de aplicación. Total backend: 94 sin fallos.
- Ingreso confirmado en Safari con la cuenta real de ADMIN. No se modificaron credenciales ni roles.

## Verificación de ADMIN

### Recorridos en Safari

- Ingreso real de ADMIN y navegación por panel, hoteles, inventario, canales, pagos, operaciones y perfil.
- Alta de hotel de prueba **QA StayHub 1710**, ID 33. Edición de ciudad a `Ciudad QA editada`.
- Alta del tipo **QA_STD**, ID 53; edición del nombre a `Habitacion QA editada`.
- Alta de habitación **QA101**, ID 122; edición de piso de 1 a 2. Tabla con cinco columnas y acciones preservadas.
- Apertura del diálogo de baja de habitación y cancelación del diálogo: no se ejecutó una baja persistente.
- Inventario del hotel 33: una unidad QA_STD del 20 al 22/10/2026, ARS 1.000 por noche. Carga HTTP 201 y consulta posterior: una unidad disponible y tarifa total ARS 2.000.
- Panel: reserva #13 confirmada, valor confirmado ARS 184.000 y llegada próxima. Tabla con siete columnas.
- Pago #9 consultable como ADMIN; búsqueda de pago inexistente oculta el resultado anterior.
- SMS simulado ejecutado con destinatario ficticio `0000000000`. El proveedor solo registra el resultado local, sin envío real.
- Booking y PMS inicialmente aceptaban solicitudes que fallaban en el consumidor por falta de configuración. Tras la corrección se comprobó HTTP 503 y mensaje específico para Booking, Expedia, Airbnb, Despegar y PMS.

### Correcciones de administración

- La invalidación de caché ahora usa las mismas claves que la lectura. Las mutaciones de tipos y habitaciones invalidan también el detalle del hotel. ADMIN consulta datos frescos.
- El saneador preserva filas y celdas al procesarlas en contexto de tabla; mantiene el filtrado de contenido y atributos peligrosos. Corrige inventario, habitaciones, reservas y disponibilidad de canales.
- Tipos y detalles de hotel descartan respuestas atrasadas. En la gestión de hotel y el inventario se bloquean acciones mientras se cargan los tipos actuales.
- Formularios de hotel/tipo/habitación bloquean envíos repetidos durante el guardado. Carga de inventario también bloquea doble envío.
- Resumen de reservas usa solo confirmadas, separa monedas y presenta el importe como valor de reservas confirmadas. Las llegadas excluyen pendientes/rechazadas.
- La sincronización distingue solicitudes en cola de finalizadas y muestra `elementosProcesados`, el campo real de la API. La configuración se valida antes de publicar el mensaje.
- Errores esperados de pagos, inventario, notificaciones y sobreventa se declaran como excepciones de aplicación EJB para conservar sus mappers.
- Las consultas de pago ocultan el resultado anterior y muestran todos los IDs en pagos de varias reservas.
- Los formularios de operaciones admiten envío por teclado y los SMS/push se identifican como simulados.

### Cobertura y límites restantes

- Backend: **99 pruebas** sin fallos/errores; interfaz: **22 pruebas** sin fallos.
- Sobreventa: pruebas automatizadas de reubicación con confirmación del hold, compensación sin alternativa y compensación por cupo agotado durante el intento. No se ejecutó una reubicación persistente sobre reservas reales desde Safari ni se enviaron avisos reales a huéspedes.
- Las bajas persistentes de hoteles/tipos/habitaciones no se ejecutaron en Safari; se verificó cancelar el diálogo de baja. No se alteraron hoteles preexistentes.
- No se recargó el catálogo demo completo: sobrescribe precios y unidades de hoteles existentes. Se verificaron altas aisladas mediante los mismos servicios.
- Falta configurar las URLs de Booking, Expedia, Airbnb y Despegar, el WSDL del PMS y la lista de hosts permitidos; sin servicios configurados no se puede confirmar una sincronización externa completa.
- Sigue pendiente SMTP para correo real y recuperación por email. SMS/push son mocks del proyecto.
- Se conservan el hotel 33, tipo 53, habitación 122 e inventario QA como evidencia; no se eliminaron datos existentes.
