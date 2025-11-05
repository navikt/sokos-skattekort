SET lock_timeout = '5s';
SET statement_timeout = '5s';

ALTER TABLE skattekort
    ALTER COLUMN utstedt_dato DROP NOT NULL;
ALTER TABLE skattekort
    ALTER COLUMN identifikator DROP NOT NULL;
