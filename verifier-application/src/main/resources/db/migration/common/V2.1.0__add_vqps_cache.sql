-- Persistent cache for Verification Query Public Statements (vqPS).
-- Primary key is query_hash (SHA-256 of DCQL query + purpose_name + purpose_description),
-- allowing multiple entries per scope when the query or metadata changes.
-- A cache hit requires an exact hash match, preventing stale vqPS JWTs from being used
-- after a DCQL query update.
CREATE TABLE vqps
(
    query_hash TEXT   NOT NULL PRIMARY KEY,
    scope      TEXT   NOT NULL,
    jwt        TEXT   NOT NULL,
    expires_at BIGINT NOT NULL
);


-- Add vqps_query_hash reference to management so that the request object service
-- can look up the correct vqPS entry by its primary key (SHA-256 hash) when building
-- the Authorization Request. Using the hash instead of the scope avoids ambiguity
-- when the DCQL query changes and multiple entries exist for the same scope.
ALTER TABLE management
    ADD COLUMN vqps_query_hash TEXT;

