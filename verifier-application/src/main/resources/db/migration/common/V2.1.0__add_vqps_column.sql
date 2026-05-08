-- Trust Protocol 2.0: store the Verification Query Public Statement (vqPS) JWT per verification session.
-- The vqPS is registered at the Trust Registry (TMS) during verification initialization and injected
-- into the verifier_info array of the JWT-Secured Authorization Request.
ALTER TABLE management
    ADD COLUMN IF NOT EXISTS vq_ps TEXT;

