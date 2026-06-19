INSERT INTO personer (id, flagget)
VALUES (1, false);

INSERT INTO foedselsnumre (id, person_id, fnr, gjelder_fom)
VALUES (1, 1, '01010112345', CURRENT_DATE);

INSERT INTO person_audit (person_id, bruker_id, tag, informasjon)
VALUES (1, 'TEST_USER', 'OPPRETTET_PERSON', 'Person 1 opprettet');

INSERT INTO bestillinger(person_id, fnr, inntektsaar, bestillingsbatch_id)
VALUES (1, '01010112345', 2025, null);