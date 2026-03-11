-- Adds the optimistic locking version column to the management table.
-- This column is incremented by JPA on every UPDATE
ALTER TABLE management
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

