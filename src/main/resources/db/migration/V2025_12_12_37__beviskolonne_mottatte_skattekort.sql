set lock_timeout = '10s';
set statement_timeout = '10s';

ALTER TABLE bestillingsbatcher ADD COLUMN IF NOT EXISTS data_mottatt TEXT NULL DEFAULT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS bestillingsbatcher_bestillingsreferanse ON bestillingsbatcher (bestillingsreferanse);