SET lock_timeout = '5s';

CREATE UNIQUE INDEX IF NOT EXISTS person_fnr_fnr ON person_fnr (fnr);
