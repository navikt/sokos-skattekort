SET lock_timeout = '5s';

-- Lag indekser i separat fil for å omgå flyways transaksjonshåndtering

CREATE INDEX IF NOT EXISTS aktoer_navn ON aktoer (navn);
CREATE INDEX IF NOT EXISTS aktoer_offnr_aktoer_id ON aktoer_offnr (aktoer_id);
CREATE INDEX IF NOT EXISTS aktoer_audit_aktoer_id ON aktoer_audit (aktoer_id);
