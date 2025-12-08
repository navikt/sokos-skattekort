-- Person uten skattekort for testing
INSERT INTO personer (id, flagget)
VALUES (1, false);

SELECT setval('personer_id_seq', (SELECT coalesce(max(id), 0) FROM personer) + 1, false);

INSERT INTO foedselsnumre (id, person_id, fnr, gjelder_fom)
VALUES (1, 1, '12345678903', CURRENT_DATE);

SELECT setval('foedselsnumre_id_seq', (SELECT coalesce(max(id), 0) FROM foedselsnumre) + 1, false);

INSERT INTO person_audit (person_id, bruker_id, tag, informasjon)
VALUES (1, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 1 opprettet');

SELECT setval('person_audit_id_seq', (SELECT coalesce(max(id), 0) FROM person_audit) + 1, false);

-- Ingen skattekort for denne personen

