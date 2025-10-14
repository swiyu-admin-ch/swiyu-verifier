ALTER TABLE management
    ADD response_specification jsonb default '{"response_mode": "direct_post"}' NOT NULL;