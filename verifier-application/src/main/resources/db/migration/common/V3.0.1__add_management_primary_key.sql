-- V3.0.1__add_management_primary_key.sql
-- Add the missing primary key on management.id
-- The table was created in V1.0.0 without a primary key, so lookups by id
-- (findById on every request-object / response call) were sequential scans
-- and uniqueness of id was not enforced.
ALTER TABLE management ADD CONSTRAINT management_pkey PRIMARY KEY (id);
