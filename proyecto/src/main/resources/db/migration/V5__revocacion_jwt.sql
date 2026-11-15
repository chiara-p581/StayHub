-- JWT revocados (logout, rotación, cambio de MFA o de rol). Cada fila vive solo hasta
-- que el token vencería solo; la aplicación borra las vencidas al guardar una nueva.
CREATE TABLE IF NOT EXISTS token_revocado (
    jti VARCHAR(64) PRIMARY KEY,
    revocado_desde BIGINT NOT NULL,
    expira_en BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_token_revocado_expira ON token_revocado(expira_en);
