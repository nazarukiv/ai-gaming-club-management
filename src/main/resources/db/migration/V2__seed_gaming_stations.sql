INSERT INTO gaming_stations (name, type, station_number, status, created_at)
SELECT 'PC-' || LPAD(number::TEXT, 2, '0'), 'PC', number, 'AVAILABLE', CURRENT_TIMESTAMP
FROM generate_series(1, 30) AS stations(number);

INSERT INTO gaming_stations (name, type, station_number, status, created_at)
SELECT 'PS5-' || LPAD(number::TEXT, 2, '0'), 'PS5', number, 'AVAILABLE', CURRENT_TIMESTAMP
FROM generate_series(1, 5) AS stations(number);
