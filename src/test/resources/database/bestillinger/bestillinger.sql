INSERT INTO bestillingsbatcher(id, bestillingsreferanse, data_sendt)
VALUES (1, 'some-bestillings-ref', '{}'),
       (2, 'other-ref', '{}');

INSERT INTO bestillinger(person_id, fnr, inntektsaar, bestillingsbatch_id)
VALUES (1, '01010112345', 2025, 1),
       (2, '02020212345', 2025, 2),
       (3, '03030312345', 2025, 2);