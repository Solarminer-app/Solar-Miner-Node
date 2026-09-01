-- User-chosen privacy level for the anonymized network location grid
-- (ARCHITECTURE §7). The full-precision coordinates are stored LOCALLY only and
-- never leave the node; the reporter quantizes them into a coarse grid code
-- (country / ~500 km regional cell / ~200 km area cell) before sending.
--
--   telemetry_geo_level: 'OFF' | 'COUNTRY' | 'REGIONAL' | 'AREA'
--   telemetry_country:  ISO-3166-1 alpha-2 (used when level = COUNTRY)
--   telemetry_lat/ lng: approximate site position (used for REGIONAL/AREA)
ALTER TABLE pv_sites
    ADD COLUMN telemetry_geo_level VARCHAR(12) NOT NULL DEFAULT 'OFF',
    ADD COLUMN telemetry_country   VARCHAR(2)  NULL,
    ADD COLUMN telemetry_lat       DOUBLE      NULL,
    ADD COLUMN telemetry_lng       DOUBLE      NULL;
