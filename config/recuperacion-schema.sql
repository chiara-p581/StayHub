-- Cambios para la recuperación de contraseña.
-- Hibernate (hbm2ddl.auto=update) los crea solo al desplegar;
-- este script es una alternativa por si hay que aplicarlos a mano.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS reset_token_hash varchar(64);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS reset_token_vencimiento timestamp;
CREATE UNIQUE INDEX IF NOT EXISTS usuario_reset_token_hash_unique ON usuario (reset_token_hash);