-- Add credential_evaluation column to management table
-- This column stores the credential_evaluation computed when completing a verification.
-- Nullable to remain backwards-compatible with existing rows.

ALTER TABLE management
    DROP COLUMN trust_anchors;