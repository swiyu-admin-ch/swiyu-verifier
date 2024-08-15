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
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.nonNull;

public class SDJWTCredentialMock {
    public static String ISSUER_ID = "TEST_ISSUER_ID";

    private final ECPrivateKey privateKey;

    private final String privateKeyString = "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEIDqMm9PvL4vpyFboAwaeViQsH30CkaDcVtRniZPezFxpoAoGCCqGSM49\nAwEHoUQDQgAEQgjeqGSdu+2jq8+n78+6fXk2Yh22lQKBYCnu5FWPvKtat3wFEsQX\nqNHYgPXBxWmOBw5l2PE/gUDUJqGJSc1LuQ==\n-----END EC PRIVATE KEY-----";

    public SDJWTCredentialMock() throws JOSEException {
        ECKey key = new ECKeyGenerator(Curve.P_256).generate();
        privateKey = getPrivateKey();
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
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            JWSSigner signer = new ECDSASigner(privateKey);
            jwt.sign(signer);
            var sdjwt = new SDJWT(jwt.serialize(), disclosures).toString();
            ObjectMapper objectMapper = new ObjectMapper();
            return Base64.getUrlEncoder().encodeToString(objectMapper.writeValueAsBytes(List.of("test", sdjwt)));
        } catch (ParseException | JOSEException | JsonProcessingException e) {
            return null;
        }
    }

    public String createSDJWTMock(Long validFrom, Long validUntil, ECPrivateKey privKey) {
        privKey = privKey != null ? privKey : privateKey;
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
            JWSSigner signer = new ECDSASigner(privKey);
            jwt.sign(signer);

            return new SDJWT(jwt.serialize(), disclosures).toString();
        } catch (ParseException | JOSEException e) {
            return null;
        }
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

    public String getNestedPresentationSubmissionString(UUID uuid) throws JsonProcessingException {
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

    private ECPrivateKey getPrivateKey() {
        ECPrivateKey key = null;
        try {
            PEMParser pemParser = new PEMParser(new StringReader(privateKeyString));
            Object object = pemParser.readObject();
            pemParser.close();

            PrivateKeyInfo privateKeyInfo;
            if (object instanceof PEMKeyPair pemKeyPair) {
                privateKeyInfo = pemKeyPair.getPrivateKeyInfo();
            } else {
                privateKeyInfo = PrivateKeyInfo.getInstance(object);
            }

            byte[] pkcs8Encoded = privateKeyInfo.getEncoded();

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Encoded);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PrivateKey generatedPrivateKey = keyFactory.generatePrivate(keySpec);

            if (generatedPrivateKey instanceof ECPrivateKey) {
                key = (ECPrivateKey) generatedPrivateKey;
            } else {
                throw new InvalidKeySpecException("The provided key is not an EC private key.");
            }
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Could not reconstruct the private key, the given algorithm could not be found.");
        } catch (InvalidKeySpecException e) {
            System.out.println("Could not reconstruct the private key");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return key;
    }

    private static HashMap<String, String> getSDClaims() {
        HashMap<String, String> claims = new HashMap<>();

        claims.put("first_name", "TestFirstname");
        claims.put("last_name", "TestLastName");
        claims.put("birthdate", "1949-01-22");

        return claims;
    }
}
