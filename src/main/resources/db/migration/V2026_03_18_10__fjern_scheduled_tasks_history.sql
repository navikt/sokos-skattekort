set statement_timeout = '10s';

CREATE INDEX IF NOT EXISTS idx_skattekort_generert_fra ON skattekort (generert_fra);

ALTER TABLE skattekort DROP CONSTRAINT IF EXISTS skattekort_generert_fra_fkey,
 ADD CONSTRAINT skattekort_generert_fra_fkey FOREIGN KEY (generert_fra)
 REFERENCES skattekort (id)
 ON DELETE CASCADE DEFERRABLE;

ALTER TABLE forskuddstrekk DROP CONSTRAINT IF EXISTS forskuddstrekk_skattekort_id_fkey,
 DROP CONSTRAINT IF EXISTS skattekort_del_skattekort_id_fkey,
 ADD CONSTRAINT forskuddstrekk_skattekort_id_fkey FOREIGN KEY (skattekort_id)
 REFERENCES skattekort (id)
 ON DELETE CASCADE DEFERRABLE;

ALTER TABLE skattekort_tilleggsopplysning DROP CONSTRAINT IF EXISTS skattekort_tilleggsopplysning_skattekort_id_fkey,
 ADD CONSTRAINT skattekort_tilleggsopplysning_skattekort_id_fkey FOREIGN KEY (skattekort_id)
 REFERENCES skattekort (id)
 ON DELETE CASCADE DEFERRABLE;

ALTER TABLE skattekort_data DROP CONSTRAINT IF EXISTS skattekort_data_skattekort_id_fkey,
 ADD CONSTRAINT skattekort_data_skattekort_id_fkey FOREIGN KEY (skattekort_id)
 REFERENCES skattekort (id)
 ON DELETE CASCADE DEFERRABLE;

