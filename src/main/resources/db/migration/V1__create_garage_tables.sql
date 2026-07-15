CREATE TABLE garage_sector (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    base_price DECIMAL(19, 4) NOT NULL,
    max_capacity INT NOT NULL,
    open_hour TIME NOT NULL,
    close_hour TIME NOT NULL,
    duration_limit_minutes INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_garage_sector PRIMARY KEY (id),
    CONSTRAINT uk_garage_sector_code UNIQUE (code),
    CONSTRAINT ck_garage_sector_base_price CHECK (base_price >= 0),
    CONSTRAINT ck_garage_sector_capacity CHECK (max_capacity > 0),
    CONSTRAINT ck_garage_sector_duration CHECK (duration_limit_minutes > 0)
);

CREATE TABLE parking_spot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_id BIGINT NOT NULL,
    sector_id BIGINT NOT NULL,
    latitude DECIMAL(11, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    occupied BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_parking_spot PRIMARY KEY (id),
    CONSTRAINT uk_parking_spot_external_id UNIQUE (external_id),
    CONSTRAINT uk_parking_spot_coordinates UNIQUE (latitude, longitude),
    CONSTRAINT fk_parking_spot_sector FOREIGN KEY (sector_id) REFERENCES garage_sector (id)
);
