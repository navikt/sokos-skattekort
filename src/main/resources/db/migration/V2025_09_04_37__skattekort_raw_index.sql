SET lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS skattekort_raw_aktoer_id ON skattekort_raw (aktoer_id);
