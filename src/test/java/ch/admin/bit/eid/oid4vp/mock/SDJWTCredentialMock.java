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
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.nonNull;

public class SDJWTCredentialMock {
    public static String ISSUER_ID = "TEST_ISSUER_ID";

    private final ECPrivateKey privateKey;
    private final ECPublicKey publicKey;

    public SDJWTCredentialMock() throws JOSEException {
        ECKey key = new ECKeyGenerator(Curve.P_256).generate();
        privateKey = key.toECPrivateKey();
        publicKey = key.toECPublicKey();
    }

    public String createNestedSDJWTMock(Long validFrom, Long validUntil) {
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

        getSDClaims().forEach((key, value) -> {
            Disclosure dis = new Disclosure(key, value);
            builder.putSDClaim(dis);
            disclosures.add(dis);
        });

        try {
            Map<String, Object> claims = builder.build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("vc+sd-jwt")).build();
            JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
            var test = new JWTClaimsSet.Builder()
                    .claim("vp", claimsSet)
                    .build();
            SignedJWT jwt = new SignedJWT(header, test);
            JWSSigner signer = new ECDSASigner(privateKey);
            jwt.sign(signer);

            return new SDJWT(jwt.serialize(), disclosures).toString();
        } catch (ParseException | JOSEException e) {
            return null;
        }
    }

    public String createSDJWTMock(Long validFrom, Long validUntil) {
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

        getSDClaims().forEach((key, value) -> {
            Disclosure dis = new Disclosure(key, value);
            builder.putSDClaim(dis);
            disclosures.add(dis);
        });

        try {
            Map<String, Object> claims = builder.build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("vc+sd-jwt")).build();
            JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            JWSSigner signer = new ECDSASigner(privateKey);
            jwt.sign(signer);

            return new SDJWT(jwt.serialize(), disclosures).toString();
        } catch (ParseException | JOSEException e) {
            return null;
        }
    }

    public String getPresentationSubmissionString(UUID uuid) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        Descriptor descriptor = Descriptor.builder()
                .path("$")
                .format("jwt_vc")
                .build();

        PresentationSubmission submission = PresentationSubmission.builder()
                .id(uuid != null ? uuid.toString() : UUID.randomUUID().toString())
                // TODO check if needed
                // .definitionId()
                .descriptorMap(List.of(descriptor))
                .build();

        return mapper.writeValueAsString(submission);
    }

    public String getNestedPresentationSubmissionString(UUID uuid) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        Descriptor nestedDescriptor = Descriptor.builder()
                .path("$.vp")
                .format("jwt_vc")
                .build();

        Descriptor descriptor = Descriptor.builder()
                .id("multipass")
                .path("$")
                .pathNested(nestedDescriptor)
                .format("jwt_vp_json")
                .build();

        PresentationSubmission submission = PresentationSubmission.builder()
                .id(uuid != null ? uuid.toString() : UUID.randomUUID().toString())
                // TODO check if needed
                // .definitionId()
                .descriptorMap(List.of(descriptor))
                .build();

        return mapper.writeValueAsString(submission);
    }

    public ECPublicKey getPublicKey() {
        return this.publicKey;
    }

    private static HashMap<String, String> getSDClaims() {
        HashMap<String, String> claims = new HashMap<>();

        claims.put("first_name", "TestFirstname");
        claims.put("last_name", "TestLastName");
        claims.put("birthdate", "1949-01-22");

        return claims;
    }
}
