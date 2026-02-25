set lock_timeout = '10s';
set statement_timeout = '10s';

ALTER TABLE skattekort_data ADD COLUMN IF NOT EXISTS fnr TEXT;
ALTER TABLE skattekort_data ADD COLUMN IF NOT EXISTS skattekort_id BIGINT REFERENCES skattekort (id) DEFERRABLE NULL;
ALTER TABLE skattekort_data ALTER COLUMN data_mottatt TYPE JSON USING NOT NULL;
ALTER TABLE skattekort_data DROP COLUMN IF EXISTS person_id;

CREATE INDEX IF NOT EXISTS skattekort_data_skattekort_id ON skattekort_data (skattekort_id);
CREATE INDEX IF NOT EXISTS skattekort_data_fnr ON skattekort_data (fnr);
