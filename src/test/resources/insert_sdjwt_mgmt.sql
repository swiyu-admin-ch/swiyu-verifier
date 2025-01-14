INSERT INTO public.management (id, request_nonce, state, requested_presentation, wallet_response, expiration_in_seconds,
                               expires_at, accepted_issuer_dids)
VALUES ('deadbeef-dead-dead-dead-deaddeafbeef', 'P2vZ8DKAtTuCIU1M7daWLA65Gzoa76tL', 'PENDING',
        '{"id": "cf244758-00f9-4fa0-83ff-6719bac358a2", "name": "Presentation Definition Name", "purpose": "Presentation Definition Purpose", "input_descriptors": [{"id": "test_descriptor_id", "purpose": "Input Descriptor Purpose", "format": {"vc+sd-jwt": {"sd-jwt_alg_values": ["ES256"], "kb-jwt_alg_values": ["ES256"]}}, "name": "Test Descriptor Name", "constraints": {"fields": [{"path": ["$"]}]}}]}',
        'null', 86400, 4070908800000, 'TEST_ISSUER_ID');
