set lock_timeout = '1s';
set statement_timeout = '5s';

CREATE INDEX IF NOT EXISTS bestillinger_inntektsaar ON bestillinger(inntektsaar);
CREATE INDEX IF NOT EXISTS bestillinger_oppdatert ON bestillinger(oppdatert);

CREATE INDEX IF NOT EXISTS skattekort_inntektsaar ON skattekort(inntektsaar);
CREATE INDEX IF NOT EXISTS skattekort_opprettet ON skattekort(opprettet);
CREATE INDEX IF NOT EXISTS skattekort_resultat ON skattekort(resultatforskattekort);

CREATE INDEX IF NOT EXISTS forskuddstrekk_trek_kode ON forskuddstrekk(trekk_kode);
CREATE INDEX IF NOT EXISTS forskuddstrekk_type ON forskuddstrekk(type);

CREATE INDEX IF NOT EXISTS skattekort_tilleggsopplysning_opplysning ON skattekort_tilleggsopplysning(opplysning);