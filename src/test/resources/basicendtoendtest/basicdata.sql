INSERT INTO personer DEFAULT VALUES;

INSERT INTO foedselsnumre (person_id, fnr)
SELECT id, '12345678901' FROM personer ORDER BY id DESC LIMIT 1;

INSERT INTO bestillinger (person_id, fnr, inntektsaar)
SELECT id, '12345678901', '2025' FROM personer ORDER BY id DESC LIMIT 1;