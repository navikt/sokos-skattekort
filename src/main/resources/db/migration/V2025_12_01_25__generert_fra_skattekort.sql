set lock_timeout = '1s';
set statement_timeout = '5s';

ALTER TABLE skattekort
    ADD COLUMN generert_fra BIGINT NULL DEFAULT NULL REFERENCES skattekort (id) DEFERRABLE;