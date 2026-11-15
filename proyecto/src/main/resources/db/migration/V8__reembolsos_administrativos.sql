ALTER TABLE pago ADD COLUMN IF NOT EXISTS fecha_reembolso TIMESTAMP;
ALTER TABLE pago ADD COLUMN IF NOT EXISTS motivo_reembolso VARCHAR(300);

ALTER TABLE pago DROP CONSTRAINT IF EXISTS ck_pago_estado;
ALTER TABLE pago ADD CONSTRAINT ck_pago_estado
    CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO','REEMBOLSADO'));

ALTER TABLE pago DROP CONSTRAINT IF EXISTS ck_pago_reembolso_completo;
ALTER TABLE pago ADD CONSTRAINT ck_pago_reembolso_completo CHECK (
    (estado = 'REEMBOLSADO' AND fecha_reembolso IS NOT NULL AND motivo_reembolso IS NOT NULL)
    OR estado <> 'REEMBOLSADO'
);
