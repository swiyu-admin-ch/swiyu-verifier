INSERT INTO public.management (id, request_nonce, state, requested_presentation, wallet_response, expiration_in_seconds,
                               jwt_secured_authorization_request, expires_at)
VALUES ('deadbeef-dead-dead-dead-deaddeafbeef', 'P2vZ8DKAtTuCIU1M7daWLA65Gzoa76tL', 'PENDING',
        '{"id": "cf244758-00f9-4fa0-83ff-6719bac358a2", "name": "Presentation Definition Name", "purpose": "Presentation Definition Purpose", "input_descriptors": [{"id": "test_descriptor_id", "purpose": "Input Descriptor Purpose", "format": {"ldp_vp": {"proof_type": ["BBS2023"]}}, "name": "Test Descriptor Name", "constraints": {"fields": [{"path": ["$.credentialSubject.hello"]}]}}]}',
        'null', 86400, false, 4070908800000);
