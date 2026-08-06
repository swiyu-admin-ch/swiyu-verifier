-- Add redirect_uri column to management table
-- This column stores the optional redirect URI provided when creating a verification management entry.
-- Nullable to remain backwards-compatible with existing rows.

ALTER TABLE management
    ADD COLUMN redirect_uri TEXT;