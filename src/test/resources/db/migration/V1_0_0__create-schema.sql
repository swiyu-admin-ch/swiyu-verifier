CREATE TABLE management (
    id uuid NOT NULL,
    request_nonce text NOT NULL,
    state text NULL,
    requested_presentation json NOT NULL,
    wallet_response json,
    expiration_in_seconds integer
);
