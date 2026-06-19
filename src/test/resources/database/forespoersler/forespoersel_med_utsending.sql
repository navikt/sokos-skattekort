INSERT INTO personer (id, flagget)
VALUES (1, false);

INSERT INTO foedselsnumre (id, person_id, fnr, gjelder_fom)
VALUES (1, 1, '01010112345', CURRENT_DATE);

INSERT INTO person_audit (person_id, bruker_id, tag, informasjon)
VALUES (1, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 1 opprettet');

INSERT INTO skattekort (id, person_id, utstedt_dato, identifikator, inntektsaar, kilde, opprettet)
VALUES (1, 1, '2025-11-11'::date, '17', 2025, 'skatteetaten', now() - interval '2 days');

INSERT INTO utsendinger (fnr, inntektsaar, forsystem)
VALUES ('01010112345', 2025, 'OS');