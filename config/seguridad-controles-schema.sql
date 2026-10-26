-- Controles KAN-48 y KAN-51. PostgreSQL 15+.
-- Ejecutar completo en una única conexión y detenerse ante cualquier error.
BEGIN;

CREATE TABLE IF NOT EXISTS seguridad_contador (
    clave_hash varchar(64) PRIMARY KEY,
    ventana_inicio bigint NOT NULL,
    cantidad integer NOT NULL CHECK (cantidad >= 0),
    vence bigint NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_seguridad_contador_vence
    ON seguridad_contador(vence);

CREATE TABLE IF NOT EXISTS seguridad_auditoria (
    id uuid PRIMARY KEY,
    fecha timestamptz NOT NULL,
    correlation_id varchar(64) NOT NULL,
    actor_id varchar(64) NOT NULL,
    rol varchar(20) NOT NULL,
    accion varchar(80) NOT NULL,
    resultado varchar(30) NOT NULL,
    recurso varchar(180) NOT NULL,
    origen_hash varchar(32) NOT NULL,
    detalle varchar(180) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_seguridad_auditoria_fecha
    ON seguridad_auditoria(fecha DESC);

CREATE INDEX IF NOT EXISTS ix_seguridad_auditoria_actor_fecha
    ON seguridad_auditoria(actor_id, fecha DESC);

CREATE INDEX IF NOT EXISTS ix_seguridad_auditoria_accion_fecha
    ON seguridad_auditoria(accion, fecha DESC);

COMMIT;
