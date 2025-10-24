SET lock_timeout = '5s';

ALTER TABLE utsendinger
    ADD COLUMN IF NOT EXISTS fail_count INT NOT NULL DEFAULT 0;
ALTER TABLE utsendinger
    ADD COLUMN IF NOT EXISTS fail_message TEXT NULL DEFAULT NULL;