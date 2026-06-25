SET lock_timeout = '5s';
set statement_timeout = '5s';

ALTER TABLE utsendinger
    ADD COLUMN IF NOT EXISTS skattekort_id BIGINT NOT NULL DEFAULT 0;
