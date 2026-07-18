CREATE TABLE parking_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    license_plate VARCHAR(20) NOT NULL,
    sector_id BIGINT NULL,
    spot_id BIGINT NULL,
    entry_time TIMESTAMP(6) NOT NULL,
    parked_at TIMESTAMP(6) NULL,
    exit_time TIMESTAMP(6) NULL,
    base_price DECIMAL(19, 4) NULL,
    price_multiplier DECIMAL(5, 2) NULL,
    hourly_price DECIMAL(19, 4) NULL,
    amount DECIMAL(19, 4) NULL,
    status VARCHAR(20) NOT NULL,
    active_license_plate VARCHAR(20)
        GENERATED ALWAYS AS (
            CASE WHEN status IN ('ENTERED', 'PARKED') THEN license_plate ELSE NULL END
        ) STORED,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_parking_session PRIMARY KEY (id),
    CONSTRAINT uk_parking_session_active_plate UNIQUE (active_license_plate),
    CONSTRAINT fk_parking_session_sector FOREIGN KEY (sector_id) REFERENCES garage_sector (id),
    CONSTRAINT fk_parking_session_spot FOREIGN KEY (spot_id) REFERENCES parking_spot (id),
    CONSTRAINT ck_parking_session_status CHECK (status IN ('ENTERED', 'PARKED', 'FINISHED')),
    CONSTRAINT ck_parking_session_base_price CHECK (base_price IS NULL OR base_price >= 0),
    CONSTRAINT ck_parking_session_multiplier CHECK (price_multiplier IS NULL OR price_multiplier > 0),
    CONSTRAINT ck_parking_session_hourly_price CHECK (hourly_price IS NULL OR hourly_price >= 0),
    CONSTRAINT ck_parking_session_amount CHECK (amount IS NULL OR amount >= 0),
    INDEX ix_parking_session_plate_status (license_plate, status),
    INDEX ix_parking_session_sector (sector_id),
    INDEX ix_parking_session_spot (spot_id)
);

CREATE TABLE processed_webhook_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(512) NOT NULL,
    idempotency_hash BINARY(32) NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    license_plate VARCHAR(20) NOT NULL,
    result VARCHAR(20) NOT NULL,
    http_status INT NOT NULL,
    operational_message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_processed_webhook_event PRIMARY KEY (id),
    CONSTRAINT uk_processed_webhook_event_hash UNIQUE (idempotency_hash),
    CONSTRAINT ck_processed_webhook_event_type CHECK (event_type IN ('ENTRY', 'PARKED', 'EXIT')),
    CONSTRAINT ck_processed_webhook_event_result CHECK (result IN ('PROCESSED', 'REJECTED')),
    INDEX ix_processed_webhook_event_plate (license_plate),
    INDEX ix_processed_webhook_event_created_at (created_at)
);
