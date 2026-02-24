set lock_timeout = '10s';
set statement_timeout = '10s';

ALTER TABLE skattekort_data ADD COLUMN IF NOT EXISTS fnr TEXT REFERENCES foedselsnumre (fnr) DEFERRABLE NOT NULL default '0';
ALTER TABLE skattekort_data ADD COLUMN IF NOT EXISTS skattekort_id BIGINT REFERENCES skattekort (id) DEFERRABLE NOT NULL default 0;
ALTER TABLE skattekort_data DROP COLUMN IF EXISTS data_mottatt;
ALTER TABLE skattekort_data ADD COLUMN IF NOT EXISTS arbeidstaker JSON NOT NULL default '{}';

