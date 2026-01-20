INSERT INTO personer DEFAULT VALUES;

INSERT INTO foedselsnumre (person_id, fnr)
SELECT id, '01010112345' FROM personer ORDER BY id DESC LIMIT 1;

INSERT INTO bestillinger (person_id, fnr, inntektsaar)
SELECT id, '01010112345', '2025' FROM personer ORDER BY id DESC LIMIT 1;