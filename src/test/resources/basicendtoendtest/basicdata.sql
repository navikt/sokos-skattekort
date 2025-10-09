INSERT INTO personer (id)
VALUES (1);

INSERT INTO foedselsnumre (person_id, fnr)
VALUES (1, '12345678901');

INSERT INTO bestillinger (person_id, fnr, inntektsaar)
VALUES (1, '12345678901', '2025');