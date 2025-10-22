SET lock_timeout = '5s';

ALTER TABLE skattekort_del RENAME TO forskuddstrekk;
ALTER INDEX skattekort_del_skattekort_id RENAME TO forskuddstrekk_skattekort_id;
ALTER SEQUENCE skattekort_del_id_seq RENAME TO forskuddstrekk_id_seq;