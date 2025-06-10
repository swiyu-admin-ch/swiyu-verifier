CREATE TABLE callback_event (
                                id uuid NOT NULL,
                                verification_id uuid NOT NULL,
                                timestamp TIMESTAMP NOT NULL
);

ALTER TABLE callback_event ADD CONSTRAINT callback_event_pkey PRIMARY KEY (id);