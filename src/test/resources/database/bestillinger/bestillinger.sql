INSERT INTO bestillingsbatcher(id, bestillingsreferanse, data_sendt)
VALUES (1, 'some-bestillings-ref', '{}'),
       (2, 'other-ref', '{}');

INSERT INTO bestillinger(person_id, fnr, inntektsaar, bestillingsbatch_id)
VALUES (1, '12345678901', 2025, 1),
       (2, '12345678902', 2025, 2),
       (3, '12345678903', 2025, 2);