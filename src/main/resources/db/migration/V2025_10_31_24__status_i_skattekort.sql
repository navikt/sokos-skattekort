SET lock_timeout = '5s';
SET statement_timeout = '5s';

ALTER TABLE skattekort ADD COLUMN resultatForSkattekort TEXT NOT NULL DEFAULT 'skattekortopplysningerOK';