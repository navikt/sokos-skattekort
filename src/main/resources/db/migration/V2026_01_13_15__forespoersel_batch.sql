set
lock_timeout = '10s';
set
statement_timeout = '10s';

ALTER TABLE bestillinger
    ADD COLUMN forespoersel_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_forespoersel FOREIGN KEY (forespoersel_id) REFERENCES forespoersler(id) DEFERRABLE;

