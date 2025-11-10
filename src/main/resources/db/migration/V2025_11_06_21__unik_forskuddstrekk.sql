SET lock_timeout = '5s';
SET statement_timeout = '5s';

CREATE UNIQUE INDEX IF NOT EXISTS forskuddstrekk_skattekort_id_trekk_kode ON forskuddstrekk (skattekort_id, trekk_kode);