set lock_timeout = '10s';
set statement_timeout = '10s';

DROP INDEX IF EXISTS bevis_sending_fnr;
DROP TABLE IF EXISTS bevis_sending CASCADE;
