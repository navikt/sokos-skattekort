SET lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS bestillinger_aktoer_id ON bestillinger (aktoer_id);
CREATE INDEX IF NOT EXISTS bestillinger_batch_bestilling_id ON bestillinger_batch (bestilling_id);