SET lock_timeout = '5s';
SET statement_timeout = '5s';

ALTER TABLE bestillingsbatcher ADD COLUMN type TEXT NOT NULL DEFAULT 'BESTILLING';
ALTER TABLE bestillingsbatcher ADD COLUMN opprettet TIMESTAMPTZ NOT NULL DEFAULT now();