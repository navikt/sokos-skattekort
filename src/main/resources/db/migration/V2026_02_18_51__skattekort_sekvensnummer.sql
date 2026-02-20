set lock_timeout = '40s';
set statement_timeout = '40s';

ALTER TABLE skattekort ADD COLUMN historisk INT NOT NULL DEFAULT 1;