-- Algunas instalaciones creadas por el esquema inicial usan este nombre
-- automático para la restricción, en lugar de ck_pago_estado de V3.
ALTER TABLE pago DROP CONSTRAINT IF EXISTS pago_estado_check;
ALTER TABLE pago DROP CONSTRAINT IF EXISTS ck_pago_estado;
ALTER TABLE pago ADD CONSTRAINT ck_pago_estado
    CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO','REEMBOLSADO'));
