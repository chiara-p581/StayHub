ALTER TABLE reserva ADD COLUMN IF NOT EXISTS clave_idempotencia VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS uk_reserva_usuario_idempotencia
    ON reserva (usuario_id, clave_idempotencia)
    WHERE clave_idempotencia IS NOT NULL;
