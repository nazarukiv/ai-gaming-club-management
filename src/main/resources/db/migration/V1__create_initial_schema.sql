CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE gaming_stations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    station_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_gaming_stations_name UNIQUE (name),
    CONSTRAINT uk_gaming_stations_type_number UNIQUE (type, station_number),
    CONSTRAINT ck_gaming_stations_type CHECK (type IN ('PC', 'PS5')),
    CONSTRAINT ck_gaming_stations_status CHECK (status IN ('AVAILABLE', 'MAINTENANCE', 'INACTIVE'))
);

CREATE TABLE bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_bookings_time_range CHECK (end_time > start_time),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED'))
);

CREATE TABLE booking_stations (
    booking_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    PRIMARY KEY (booking_id, station_id),
    CONSTRAINT fk_booking_stations_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_stations_station FOREIGN KEY (station_id) REFERENCES gaming_stations (id) ON DELETE RESTRICT
);

CREATE TABLE activity_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_activity_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- Primary keys and unique constraints already create their own indexes.
CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_booking_stations_station_id ON booking_stations (station_id);
CREATE INDEX idx_activity_logs_user_id ON activity_logs (user_id);
