SET lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS bestillinger_aktoer_id ON bestillinger (aktoer_id);
CREATE INDEX IF NOT EXISTS bestillinger_bestillinger_batch_id ON bestillinger (bestilling_batch_id);
CREATE UNIQUE INDEX IF NOT EXISTS bestillinger_fnr_aar_unique ON bestillinger (aktoer_id, fnr, aar);
