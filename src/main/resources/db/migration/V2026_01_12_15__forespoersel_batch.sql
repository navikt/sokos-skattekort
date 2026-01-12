set lock_timeout = '10s';
set statement_timeout = '10s';

ALTER TABLE forespoersler
    ADD COLUMN batch BOOLEAN DEFAULT FALSE;
