-- Ejecutar con la cuenta de migraciones, nunca con el usuario de la aplicación.
ALTER TABLE pago ADD COLUMN IF NOT EXISTS usuario_id BIGINT;

-- Compatibilidad con pagos ya existentes: el propietario se deriva de la reserva principal.
UPDATE pago p
SET usuario_id = r.usuario_id
FROM reserva r
WHERE p.usuario_id IS NULL AND r.id = p.reserva_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pago WHERE usuario_id IS NULL) THEN
        RAISE EXCEPTION 'No se puede endurecer pago: existen filas sin propietario derivable';
    END IF;
END $$;

ALTER TABLE pago ALTER COLUMN usuario_id SET NOT NULL;
DROP INDEX IF EXISTS uk_pago_clave_idempotencia;
ALTER TABLE pago DROP CONSTRAINT IF EXISTS uk_pago_clave_idempotencia;
CREATE UNIQUE INDEX IF NOT EXISTS uk_pago_usuario_idempotencia
    ON pago (usuario_id, clave_idempotencia)
    WHERE clave_idempotencia IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pago_reserva_item
    ON pago_reserva (pago_id, reserva_id);

ALTER TABLE pago DROP CONSTRAINT IF EXISTS ck_pago_monto_positivo;
ALTER TABLE pago ADD CONSTRAINT ck_pago_monto_positivo CHECK (monto > 0);
ALTER TABLE pago DROP CONSTRAINT IF EXISTS ck_pago_moneda_iso;
ALTER TABLE pago ADD CONSTRAINT ck_pago_moneda_iso CHECK (moneda ~ '^[A-Z]{3}$');
ALTER TABLE pago DROP CONSTRAINT IF EXISTS ck_pago_estado;
ALTER TABLE pago ADD CONSTRAINT ck_pago_estado CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO'));

CREATE INDEX IF NOT EXISTS ix_pago_usuario_fecha ON pago (usuario_id, fecha_pago DESC);
CREATE INDEX IF NOT EXISTS ix_pago_reserva_estado ON pago (reserva_id, estado);