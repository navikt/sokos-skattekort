set lock_timeout = '1s';
set statement_timeout = '5s';

ALTER TABLE utsendinger ALTER COLUMN abonnement_id DROP NOT NULL;