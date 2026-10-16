-- PostgreSQL: batch de 10 instrucciones sin bloques DO ni dollar quoting.
-- Ejecutar completo en la misma conexión, en orden y deteniéndose ante un error.
BEGIN;

ALTER TABLE reserva ADD COLUMN IF NOT EXISTS usuario_id bigint;

ALTER TABLE reserva DROP CONSTRAINT IF EXISTS fk_reserva_usuario_seguridad_a;

ALTER TABLE reserva ADD CONSTRAINT fk_reserva_usuario_seguridad_a
    FOREIGN KEY (usuario_id) REFERENCES usuario(id);

CREATE INDEX IF NOT EXISTS ix_reserva_usuario_estado ON reserva(usuario_id, estado, id);

CREATE INDEX IF NOT EXISTS ix_reserva_usuario_listado ON reserva(usuario_id, id DESC);

CREATE INDEX IF NOT EXISTS ix_reserva_hotel_listado ON reserva(hotel_id, id DESC);

CREATE INDEX IF NOT EXISTS ix_reserva_llegadas ON reserva(hotel_id, check_in, estado, id);

CREATE INDEX IF NOT EXISTS ix_reserva_pendiente_expira ON reserva(fecha_creacion, id)
    WHERE estado = 'PENDIENTE' AND usuario_id IS NOT NULL;

COMMIT;

-- Las reservas antiguas conservan usuario_id=NULL. No asignar por coincidencia
-- de email: antes de SEC-02 ese email podía venir de cualquier cliente.
-- Elaborar y aprobar un mapa reserva_id -> usuario_id con evidencia confiable;
-- ejecutar cada asignación solo si usuario_id IS NULL, dentro de una transacción.
-- Las reservas OTA permanecen sin propietario huésped salvo vinculación revisada.
