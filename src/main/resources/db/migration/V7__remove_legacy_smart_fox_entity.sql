-- V0 is an already released Flyway migration and must keep its original checksum.
-- Remove its obsolete joined-inheritance table from the effective schema here.
DROP TABLE IF EXISTS smart_fox_smart_meter;
DROP TABLE IF EXISTS smart_fox_battery;
DROP TABLE IF EXISTS smart_fox_inverter;
DROP TABLE IF EXISTS smart_fox_entity;
