BEGIN;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS mfa_secret_cifrado VARCHAR(512);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS mfa_habilitado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS mfa_codigo_recuperacion (
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    codigo_hash VARCHAR(64) NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    usado_en TIMESTAMP(6) WITH TIME ZONE,
    PRIMARY KEY (usuario_id, codigo_hash)
);

CREATE INDEX IF NOT EXISTS ix_mfa_codigo_pendiente
    ON mfa_codigo_recuperacion(usuario_id, usado);
COMMIT;
