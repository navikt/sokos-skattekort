SET lock_timeout = '5s';
set statement_timeout = '5s';

ALTER TABLE utsendinger
    ADD COLUMN if not exists skattekort_id BIGINT;

WITH missing AS (
    SELECT id, fnr, inntektsaar
    FROM utsendinger
    WHERE skattekort_id IS NULL
),
latest_skattekort_per_utsending AS (
    SELECT DISTINCT ON (m.id)
        m.id AS utsending_id,
        s.id AS skattekort_id
    FROM missing m
        JOIN foedselsnumre f
    ON f.fnr = m.fnr
        JOIN skattekort s
        ON s.person_id = f.person_id
        AND s.inntektsaar = m.inntektsaar
    ORDER BY m.id, s.opprettet DESC, s.id DESC
)
UPDATE utsendinger u
SET skattekort_id = ls.skattekort_id
FROM latest_skattekort_per_utsending ls
WHERE u.id = ls.utsending_id
  AND u.skattekort_id IS NULL;

ALTER TABLE utsendinger
    ADD CONSTRAINT utsendinger_skattekort_id_not_null
        CHECK (skattekort_id IS NOT NULL) NOT VALID;

ALTER TABLE utsendinger
    VALIDATE CONSTRAINT utsendinger_skattekort_id_not_null;