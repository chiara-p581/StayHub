ALTER TABLE pago ADD COLUMN IF NOT EXISTS clave_idempotencia VARCHAR(80);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pago_clave_idempotencia
    ON pago (clave_idempotencia) WHERE clave_idempotencia IS NOT NULL;
