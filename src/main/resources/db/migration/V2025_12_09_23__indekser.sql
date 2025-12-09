set lock_timeout = '10s';
set statement_timeout = '10s';

CREATE INDEX CONCURRENTLY IF NOT EXISTS bestillinger_inntektsaar ON bestillinger(inntektsaar);
CREATE INDEX CONCURRENTLY IF NOT EXISTS bestillinger_oppdatert ON bestillinger(oppdatert);

CREATE INDEX CONCURRENTLY IF NOT EXISTS skattekort_inntektsaar ON skattekort(inntektsaar);
CREATE INDEX CONCURRENTLY IF NOT EXISTS skattekort_opprettet ON skattekort(opprettet);
CREATE INDEX CONCURRENTLY IF NOT EXISTS skattekort_resultat ON skattekort(resultatforskattekort);

CREATE INDEX CONCURRENTLY IF NOT EXISTS forskuddstrekk_trek_kode ON forskuddstrekk(trekk_kode);
CREATE INDEX CONCURRENTLY IF NOT EXISTS forskuddstrekk_type ON forskuddstrekk(type);

CREATE INDEX CONCURRENTLY IF NOT EXISTS skattekort_tilleggsopplysning_opplysning ON skattekort_tilleggsopplysning(opplysning);