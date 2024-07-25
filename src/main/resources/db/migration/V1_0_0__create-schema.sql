CREATE TABLE management (
    id uuid NOT NULL,
    request_nonce text NOT NULL,
    state text NULL,
    requested_presentation jsonb NOT NULL,
    wallet_response jsonb,
    expiration_in_seconds integer
);
