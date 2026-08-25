-- Add response_code column to management table
-- This column stores the optional response code provided when successfully completing a verification.
-- Nullable to remain backwards-compatible with existing rows.

ALTER TABLE management
    ADD COLUMN credential_evaluation jsonb;