set lock_timeout = '10s';
set statement_timeout = '10s';

ALTER TABLE skattekort_data ADD COLUMN IF NOT EXISTS type TEXT;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS utsendinger_unique ON utsendinger (fnr, inntektsaar, forsystem);
