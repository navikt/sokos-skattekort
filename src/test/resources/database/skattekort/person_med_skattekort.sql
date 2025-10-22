INSERT INTO personer (id, flagget)
VALUES (1, false),
       (2, false),
       (3, false);

SELECT setval('personer_id_seq', (SELECT coalesce(max(id), 0) FROM personer) + 1, false);

INSERT INTO foedselsnumre (id, person_id, fnr, gjelder_fom)
VALUES (1, 1, '12345678901', CURRENT_DATE),
       (2, 2, '12345678902', CURRENT_DATE),
       (3, 3, '12345678903', CURRENT_DATE);

SELECT setval('foedselsnumre_id_seq', (SELECT coalesce(max(id), 0) FROM foedselsnumre) + 1, false);

INSERT INTO person_audit (person_id, bruker_id, tag, informasjon)
VALUES (1, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 1 opprettet'),
       (2, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 2 opprettet'),
       (3, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 3 opprettet');

INSERT INTO person_audit (person_id, bruker_id, tag, informasjon)
VALUES (1, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 1 opprettet'),
       (2, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 2 opprettet'),
       (3, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 3 opprettet');

SELECT setval('person_audit_id_seq', (SELECT coalesce(max(id), 0) FROM person_audit) + 1, false);

-- Skattekort testdata
INSERT INTO skattekort (id, person_id, utstedt_dato, identifikator, inntektsaar, kilde, opprettet)
VALUES
    -- Person 1: two years (current and previous) to test selection of latest per inntektsår
    (1, 1, CURRENT_DATE, 'SKK-P1-2025-A', 2025, 'skatt', now() - interval '2 days'),
    (2, 1, CURRENT_DATE - interval '370 days', 'SKK-P1-2024-A', 2024, 'skatt', now() - interval '370 days'),
    -- Person 2: single synthetic card
    (3, 2, CURRENT_DATE, 'SKK-P2-2025-S', 2025, 'syntetisert', now() - interval '1 day'),
    -- Person 3: manually adjusted card
    (4, 3, CURRENT_DATE, 'SKK-P3-2025-M', 2025, 'manuelt', now() - interval '1 hour');

SELECT setval('skattekort_id_seq', (SELECT coalesce(max(id), 0) FROM skattekort) + 1, false);

-- Skattekort del (frikort, tabell, prosent)
INSERT INTO forskuddstrekk (id, skattekort_id, trekk_kode, type, frikort_beloep, tabell_nummer, prosentsats, antall_mnd_for_trekk)
VALUES
    -- Person 1, 2025 (tabellkort)
    (1, 1, 'LOENN_FRA_HOVEDARBEIDSGIVER', 'tabell', NULL, '7100', 27.5, 12),
    -- Person 1, 2024 (prosentkort)
    (2, 2, 'LOENN_FRA_HOVEDARBEIDSGIVER', 'prosent', NULL, NULL, 30.0, 12),
    -- Person 2, 2025 (frikort + biarbeidsgiver prosent)
    (3, 3, 'LOENN_FRA_HOVEDARBEIDSGIVER', 'frikort', 65000, NULL, NULL, NULL),
    (4, 3, 'LOENN_FRA_BIARBEIDSGIVER', 'prosent', NULL, NULL, 28.0, 10.5),
    -- Person 3, 2025 (pensjon prosent)
    (5, 4, 'PENSJON_FRA_NAV', 'prosent', NULL, NULL, 18.5, 12);

SELECT setval('forskuddstrekk_id_seq', (SELECT coalesce(max(id), 0) FROM forskuddstrekk) + 1, false);

-- Tilleggsopplysninger
INSERT INTO skattekort_tilleggsopplysning (id, skattekort_id, opplysning)
VALUES (1, 1, 'kildeskattPaaLoenn'),
       (2, 3, 'kildeskattPaaLoenn'),
       (3, 4, 'kildeskattPaaPensjon');

SELECT setval('skattekort_tilleggsopplysning_id_seq', (SELECT coalesce(max(id), 0) FROM skattekort_tilleggsopplysning) + 1, false);

-- Rå skattekortdata (slik mottatt fra ekstern kilde)
INSERT INTO skattekort_data (id, person_id, inntektsaar, data_mottatt, opprettet)
VALUES (1, 1, 2025, '{"identifikator":"SKK-P1-2025-A","kilde":"skatt"}', now() - interval '2 days'),
       (2, 1, 2024, '{"identifikator":"SKK-P1-2024-A","kilde":"skatt"}', now() - interval '370 days'),
       (3, 2, 2025, '{"identifikator":"SKK-P2-2025-S","kilde":"syntetisert"}', now() - interval '1 day'),
       (4, 3, 2025, '{"identifikator":"SKK-P3-2025-M","kilde":"manuelt"}', now() - interval '1 hour');

SELECT setval('skattekort_data_id_seq', (SELECT coalesce(max(id), 0) FROM skattekort_data) + 1, false);