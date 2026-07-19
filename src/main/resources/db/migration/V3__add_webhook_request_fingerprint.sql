ALTER TABLE processed_webhook_event
    ADD COLUMN request_fingerprint BINARY(32) NULL AFTER idempotency_hash;

UPDATE processed_webhook_event
SET request_fingerprint = UNHEX(SHA2(CONCAT('LEGACY|', id), 256))
WHERE request_fingerprint IS NULL;

ALTER TABLE processed_webhook_event
    MODIFY COLUMN request_fingerprint BINARY(32) NOT NULL;
