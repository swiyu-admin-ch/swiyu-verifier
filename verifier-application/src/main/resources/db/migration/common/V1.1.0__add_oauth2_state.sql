-- Adds RFC6749 (OAuth 2.0) state to be stored with the session
ALTER TABLE management
    ADD COLUMN oauth_state text DEFAULT gen_random_uuid();

