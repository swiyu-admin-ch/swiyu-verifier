-- Move old column away
ALTER TABLE management RENAME COLUMN accepted_issuer_dids TO accepted_issuer_dids_old;
-- Add new column with array data type
ALTER TABLE management ADD COLUMN accepted_issuer_dids TEXT ARRAY;
-- Migrate old data
UPDATE management
SET accepted_issuer_dids = regexp_split_to_array(accepted_issuer_dids_old, E',');