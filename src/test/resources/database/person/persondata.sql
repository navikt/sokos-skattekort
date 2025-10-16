INSERT INTO personer (id, flagget)
VALUES (1, false),
       (2, false),
       (3, false),
       (4, false),
       (5, false),
       (6, false),
       (7, false),
       (8, false),
       (9, false),
       (10, false);

SELECT setval('personer_id_seq', (SELECT coalesce(max(id), 0) FROM personer) + 1, false);

INSERT INTO foedselsnumre (id, person_id, fnr, gjelder_fom)
VALUES (1, 1, '01010100001', CURRENT_DATE),
       (2, 2, '02020200002', CURRENT_DATE),
       (3, 3, '03030300003', CURRENT_DATE),
       (4, 4, '04040400004', CURRENT_DATE),
       (5, 5, '05050500005', CURRENT_DATE),
       (6, 6, '06060600006', CURRENT_DATE),
       (7, 7, '07070700007', CURRENT_DATE),
       (8, 8, '08080800008', CURRENT_DATE),
       (9, 9, '09090900009', CURRENT_DATE),
       (10, 10, '10101000010', CURRENT_DATE);

SELECT setval('foedselsnumre_id_seq', (SELECT coalesce(max(id), 0) FROM foedselsnumre) + 1, false);

INSERT INTO person_audit (person_id, bruker_id, tag, informasjon)
VALUES (1, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 1 opprettet'),
       (2, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 2 opprettet'),
       (3, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 3 opprettet'),
       (4, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 4 opprettet'),
       (5, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 5 opprettet'),
       (6, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 6 opprettet'),
       (7, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 7 opprettet'),
       (8, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 8 opprettet'),
       (9, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 9 opprettet'),
       (10, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 10 opprettet');

SELECT setval('person_audit_id_seq', (SELECT coalesce(max(id), 0) FROM person_audit) + 1, false);