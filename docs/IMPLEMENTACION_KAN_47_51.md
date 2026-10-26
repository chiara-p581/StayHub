# Implementación KAN-47 a KAN-51

## Alcance implementado

### KAN-47 — PBKDF2 con pocas iteraciones

- Los hashes nuevos usan `PBKDF2WithHmacSHA256` con 600.000 iteraciones.
- Los hashes versionados anteriores siguen verificándose con el costo guardado en
  el propio hash y se migran automáticamente al iniciar sesión correctamente.
- Sal, comparación en tiempo constante y formato versionado se conservan.

### KAN-48 — Rate limiting local y reiniciable

- Los contadores se almacenan en PostgreSQL mediante un UPSERT atómico.
- El límite queda compartido entre nodos y no se pierde al reiniciar o desplegar.
- Se usan claves SHA-256, sin persistir emails, IPs ni nonces en claro.
- Login, límites HTTP, notificaciones, correo, uso único de TOTP y replay de
  webhooks OTA usan el mismo backend durable.
- Una tarea elimina contadores vencidos cada hora.

### KAN-49 — Administradores sin MFA ni reautenticación

- Todo login de rol `ADMIN` exige un código TOTP de seis dígitos.
- El código admite una ventana de un paso anterior/posterior y no puede reutilizarse.
- Las sesiones administrativas sin evidencia de MFA son rechazadas.
- Crear/modificar/eliminar hoteles, tipos y habitaciones, cargar inventario,
  sincronizar canales, enviar notificaciones, resolver overbooking y cargar datos
  demo exigen contraseña y un nuevo TOTP si pasaron más de diez minutos.
- La interfaz lleva al administrador al formulario de confirmación y luego vuelve
  a la pantalla original sin guardar contraseña, código ni secreto.

### KAN-50 — Autorización administrativa dependiente de rutas

- Se eliminaron las listas de rutas del filtro de autorización.
- Cada recurso o método declara `@PermitAll`, `@RolesAllowed`,
  `@WebhookFirmado` y, cuando corresponde, `@ReautenticacionReciente`.
- La ruta sólo se interpreta para obtener el canal que participa en la firma
  criptográfica de un webhook; no decide el rol requerido.

### KAN-51 — Auditoría solamente local

- Cada evento conserva el log estructurado local y también se inserta en
  `seguridad_auditoria` mediante una transacción independiente.
- La copia central incluye fecha, correlación, actor, rol, acción, resultado,
  recurso, huella truncada del origen y detalle saneado; no guarda cuerpos,
  contraseñas, tokens ni IPs en claro.
- Si el almacenamiento central no está disponible, el evento local permanece y
  se emite `AUDITORIA_CENTRAL_NO_DISPONIBLE` sin revelar el evento ni el error.

## Activación obligatoria

1. Aplicar `config/seguridad-controles-schema.sql` a la misma base PostgreSQL que
   usa `StayHubDS` antes de desplegar el WAR.
2. Generar un secreto por cada administrador:

   ```sh
   python3 config/generar-mfa-admin.py --usuario-id 1 --email admin@stayhub.com
   ```

3. Abrir el archivo privado `.local-security/mfa-admin-1.txt`, importar la URI
   `otpauth://` en la aplicación autenticadora y configurar la variable indicada
   en el entorno **del proceso de WildFly**. No copiar el secreto al repositorio,
   al XML del servidor ni a argumentos de comandos.
4. Reiniciar WildFly para que reciba la variable y desplegar el WAR.

El nombre esperado para el administrador con ID `1` es
`STAYHUB_MFA_ADMIN_1_SECRET`; para otros IDs cambia únicamente el número.

## Verificación

- `mvn test` cubre costo y migración PBKDF2, contadores compartidos, TOTP y
  anti-replay, vigencia de reautenticación y presencia de metadatos de acceso.
- `npm test` en `proyecto/ui-security` valida CSP, Web Storage, flujo CSRF y la
  interfaz.
- La verificación de despliegue debe confirmar que la migración está aplicada,
  que un administrador sin MFA no inicia sesión, que una acción sensible vencida
  solicita reautenticación y que el evento aparece en `seguridad_auditoria`.
