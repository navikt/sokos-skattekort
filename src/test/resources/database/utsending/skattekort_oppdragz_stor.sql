INSERT INTO forespoersler (id, data_mottatt, forsystem)
VALUES (4001, '', 'OS_STOR');

INSERT INTO abonnementer (id, forespoersel_id, person_id, inntektsaar)
VALUES (5001, 4001, 3, 2025);

INSERT INTO utsendinger (fnr, inntektsaar, forsystem, skattekort_id)
VALUES ('03030312345', 2025, 'OS_STOR', 4);

