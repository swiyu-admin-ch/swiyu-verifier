package ch.admin.bit.eid.oid4vp.mock;

import ch.admin.bit.eid.oid4vp.model.dto.Descriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDJWT;
import com.authlete.sd.SDObjectBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;

import static java.util.Objects.nonNull;

public class SDJWTCredentialMock {
    public static String ISSUER_ID = "TEST_ISSUER_ID";

    @Getter
    private final ECKey key;
    @Getter
    private final ECKey holderKey;

    private final String privateKeyString = "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEIDqMm9PvL4vpyFboAwaeViQsH30CkaDcVtRniZPezFxpoAoGCCqGSM49\nAwEHoUQDQgAEQgjeqGSdu+2jq8+n78+6fXk2Yh22lQKBYCnu5FWPvKtat3wFEsQX\nqNHYgPXBxWmOBw5l2PE/gUDUJqGJSc1LuQ==\n-----END EC PRIVATE KEY-----";

    public SDJWTCredentialMock() throws JOSEException {
        holderKey = new ECKeyGenerator(Curve.P_256).generate();
        key = JWK.parseFromPEMEncodedObjects(privateKeyString).toECKey();
    }

    /**
     * Adds A second (fake) VC to the VP Token generated to test the Presentation Exchange Credential Selection
     * @param sdjwt
     * @return
     */
    public String createMultipleVPTokenMock(String sdjwt) throws JsonProcessingException {
            ObjectMapper objectMapper = new ObjectMapper();
            return Base64.getUrlEncoder().encodeToString(objectMapper.writeValueAsBytes(List.of("test", sdjwt)));
    }

    public String createSDJWTMock(Long validFrom, Long validUntil, ECKey privKey) throws JOSEException {
        privKey = privKey != null ? privKey : key;
        SDObjectBuilder builder = new SDObjectBuilder();
        List<Disclosure> disclosures = new ArrayList<>();

        builder.putClaim("iss", ISSUER_ID);
        builder.putClaim("iat", Instant.now().getEpochSecond());

        if (nonNull(validFrom)) {
            builder.putClaim("nbf", validFrom);
        }

        if (nonNull(validUntil)) {
            builder.putClaim("exp", validUntil);
        }

        builder.putClaim("cnf", holderKey.toPublicJWK().toJSONObject());

        getSDClaims().forEach((k, v) -> {
            Disclosure dis = new Disclosure(k, v);
            builder.putSDClaim(dis);
            disclosures.add(dis);
        });

        try {
            Map<String, Object> claims = builder.build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("vc+sd-jwt")).build();
            JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            JWSSigner signer = new ECDSASigner(privKey);
            jwt.sign(signer);

            return new SDJWT(jwt.serialize(), disclosures).toString();
        } catch (ParseException | JOSEException e) {
            return null;
        }
    }

    /**
     * Adds a key binding proof the SD-JWT.
     * @param sdjwt a string {jwt}~{disclosure-1}~...{disclosure-n}~
     * @return complete sdjwt with key binding as a string {jwt}~{disclosure-1}~...{disclosure-n}~{keyBindingProof}
     */
    public String addKeyBindingProof(String sdjwt, String nonce, String aud) throws NoSuchAlgorithmException, ParseException, JOSEException {
        // Create hash, hope not to have any indigestion
        var hash = new String(Base64.getUrlEncoder().withoutPadding().encode(MessageDigest.getInstance("sha-256").digest(sdjwt.getBytes())));
        HashMap<String, Object> proofData = new HashMap<>();
        proofData.put("sd_hash", hash);
        proofData.put("iat", Instant.now().getEpochSecond());
        proofData.put("aud", aud);
        proofData.put("nonce", nonce);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("kb+jwt")).build();
        var jwt = new SignedJWT(header, JWTClaimsSet.parse(proofData));
        jwt.sign(new ECDSASigner(holderKey));
        return sdjwt+jwt.serialize();
    }

    public String getPresentationSubmissionString(UUID uuid) throws JsonProcessingException {

        return getPresentationSubmissionStringWithPath(uuid != null ? uuid : UUID.randomUUID(), "$");
    }

    public String getPresentationSubmissionStringWithPath(UUID uuid, String path) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        Descriptor descriptor = Descriptor.builder()
                .path(path)
                .format("jwt_vc")
                .build();

        PresentationSubmission submission = PresentationSubmission.builder()
                .id(uuid != null ? uuid.toString() : UUID.randomUUID().toString())
                .descriptorMap(List.of(descriptor))
                .build();

        return mapper.writeValueAsString(submission);
    }

    public String getMultiplePresentationSubmissionString(UUID uuid) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        Descriptor descriptorSDJWT = Descriptor.builder()
                .id("multipass")
                .path("$[1]")
                .format("jwt_vc")
                .build();

        Descriptor descriptor = Descriptor.builder()
                .id("test")
                .path("$[0]")
                .format("whatever")
                .build();
        PresentationSubmission submission = PresentationSubmission.builder()
                .id(uuid != null ? uuid.toString() : UUID.randomUUID().toString())
                .descriptorMap(List.of(descriptorSDJWT, descriptor))
                .build();

        return mapper.writeValueAsString(submission);
    }

    private static HashMap<String, String> getSDClaims() {
        HashMap<String, String> claims = new HashMap<>();

        claims.put("first_name", "TestFirstname");
        claims.put("last_name", "TestLastName");
        claims.put("birthdate", "1949-01-22");

        return claims;
    }
}
