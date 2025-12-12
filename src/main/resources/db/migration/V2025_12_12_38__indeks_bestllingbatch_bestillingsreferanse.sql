set lock_timeout = '40s';
set statement_timeout = '40s';

CREATE INDEX IF NOT EXISTS bestillingsbatcher_bestillingsreferanse ON bestillingsbatcher (bestillingsreferanse);