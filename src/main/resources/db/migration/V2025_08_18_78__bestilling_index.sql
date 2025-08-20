SET lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS ix_fk_bestilling_id ON bestilling (id)
