/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.test.mock;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;

import static java.util.Objects.nonNull;

import ch.admin.bj.swiyu.verifier.oid4vp.api.submission.DescriptorDto;
import ch.admin.bj.swiyu.verifier.oid4vp.api.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.KeyFixtures;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDJWT;
import com.authlete.sd.SDObjectBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;

@Getter
public class SDJWTCredentialMock {
    public static final String DEFAULT_ISSUER_ID = "TEST_ISSUER_ID";
    public static final String DEFAULT_KID_HEADER_VALUE = DEFAULT_ISSUER_ID + "#key-1";
    public static final String DEFAULT_VCT = "defaultTestVCT";

    private final ECKey key;
    private final ECKey holderKey;
    private final String issuerId;
    /**
     * The kidHeaderValue is a string that represents the Key ID (KID) header in the JWT header. This value is used to
     * identify the key used to sign the JWT and reference it in the DID document.
     * <p>
     * See <a href="https://tools.ietf.org/html/rfc7515#section-4.1.4">RFC7515</a> for more information.
     */
    private final String kidHeaderValue;

    public SDJWTCredentialMock() {
        this(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE, KeyFixtures.issuerKey(), KeyFixtures.holderKey());
    }

    public SDJWTCredentialMock(ECKey key) {
        this(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE, key, KeyFixtures.holderKey());
    }

    public SDJWTCredentialMock(String issuerId, String kidHeaderValue) {
        this(issuerId, kidHeaderValue, KeyFixtures.issuerKey(), KeyFixtures.holderKey());
    }

    public SDJWTCredentialMock(String issuerId, String kidHeaderValue, ECKey key, ECKey holderKey) {
        this.key = key;
        this.holderKey = holderKey;
        this.issuerId = issuerId;
        this.kidHeaderValue = kidHeaderValue;
    }

    public static String getPresentationSubmissionString(UUID uuid) throws JsonProcessingException {
        return getPresentationSubmissionStringWithPath(uuid != null ? uuid : UUID.randomUUID(), "$");
    }

    public static String getPresentationSubmissionStringWithPath(UUID uuid, String path) throws JsonProcessingException {
        var mapper = new ObjectMapper();
        var submission = getPresentationSubmissionWithPath(uuid, path);
        return mapper.writeValueAsString(submission);
    }

    public static PresentationSubmissionDto getPresentationSubmissionWithPath(UUID uuid, String path) {
        DescriptorDto descriptor = DescriptorDto.builder()
                .path(path)
                .format(SdjwtCredentialVerifier.CREDENTIAL_FORMAT)
                .build();

        return PresentationSubmissionDto.builder()
                .id(uuid != null ? uuid.toString() : UUID.randomUUID().toString())
                .descriptorMap(List.of(descriptor))
                .build();
    }

    public static String getMultiplePresentationSubmissionString(UUID uuid) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        DescriptorDto descriptorSDJWT = DescriptorDto.builder()
                .id("multipass")
                .path("$[1]")
                .format(SdjwtCredentialVerifier.CREDENTIAL_FORMAT)
                .build();

        DescriptorDto descriptor = DescriptorDto.builder()
                .id("test")
                .path("$[0]")
                .format("whatever")
                .build();
        PresentationSubmissionDto submission = PresentationSubmissionDto.builder()
                .id(uuid != null ? uuid.toString() : UUID.randomUUID().toString())
                .descriptorMap(List.of(descriptorSDJWT, descriptor))
                .build();

        return mapper.writeValueAsString(submission);
    }

    /**
     * Adds A second (fake) VC to the VP Token generated to test the Presentation Exchange Credential Selection
     *
     * @param sdjwt full sd jwt serialized as string
     */
    public static String createMultipleVPTokenMock(String sdjwt) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return Base64.getUrlEncoder().encodeToString(objectMapper.writeValueAsBytes(List.of("test", sdjwt)));
    }

    public String createSDJWTMock(Integer statusListIndex) {
        return createSDJWTMock(null, null, statusListIndex, "testCredentialType", getSDClaims());
    }

    public String createSDJWTMock() {
        return createSDJWTMock(null, null, null, DEFAULT_VCT, getSDClaims());
    }

    public String createSDJWTMock(Long validFrom) {
        return createSDJWTMock(validFrom, null, null, DEFAULT_VCT, getSDClaims());
    }

    public String createSDJWTMock(Long validFrom, Long validUntil) {
        return createSDJWTMock(validFrom, validUntil, null, DEFAULT_VCT, getSDClaims());
    }

    /**
     * Creates a VC which has a selective disclosure overriding the issuer claim of the jwt
     */
    public String createIssuerAttackSDJWTMock() {
        var sdClaims = getSDClaims();
        sdClaims.put("iss", "did:example:12344321");
        return createSDJWTMock(null, null, null, DEFAULT_VCT, sdClaims);
    }

    public String createIllegalSDJWTMock() {
        var sdClaims = getSDClaims();
        sdClaims.put("iss", "did:example:12344321");
        return createIllegalSDJWTMock(DEFAULT_VCT, sdClaims);
    }

    /**
     * Adds a key binding proof the SD-JWT.
     *
     * @param sdjwt a string {jwt}~{disclosure-1}~...{disclosure-n}~
     * @return complete sdjwt with key binding as a string {jwt}~{disclosure-1}~...{disclosure-n}~{keyBindingProof}
     */
    public String addKeyBindingProof(String sdjwt, String nonce, String aud) throws NoSuchAlgorithmException, ParseException, JOSEException {
        return addKeyBindingProof(sdjwt, nonce, aud, Instant.now().getEpochSecond());
    }

    public String addKeyBindingProof(String sdjwt, String nonce, String aud, long iat) throws NoSuchAlgorithmException, ParseException, JOSEException {
        // Create hash, hope not to have any indigestion
        var hash = new String(Base64.getUrlEncoder().withoutPadding().encode(MessageDigest.getInstance("sha-256").digest(sdjwt.getBytes())));
        HashMap<String, Object> proofData = new HashMap<>();
        proofData.put("sd_hash", hash);
        proofData.put("iat", iat);
        proofData.put("aud", aud);
        proofData.put("nonce", nonce);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("kb+jwt")).build();
        var jwt = new SignedJWT(header, JWTClaimsSet.parse(proofData));
        jwt.sign(new ECDSASigner(holderKey));
        return sdjwt + jwt.serialize();
    }

    private static HashMap<String, String> getSDClaims() {
        HashMap<String, String> claims = new HashMap<>();

        claims.put("first_name", "TestFirstname");
        claims.put("last_name", "TestLastName");
        claims.put("birthdate", "1949-01-22");

        return claims;
    }

    private String createSDJWTMock(Long validFrom, Long validUntil, Integer statusListIndex, String vct, HashMap<String, String> sdClaims) {
        SDObjectBuilder builder = new SDObjectBuilder();
        List<Disclosure> disclosures = new ArrayList<>();

        sdClaims.forEach((k, v) -> {
            Disclosure dis = new Disclosure(k, v);
            builder.putSDClaim(dis);
            disclosures.add(dis);
        });

        builder.putClaim("iss", issuerId);
        builder.putClaim("iat", Instant.now().getEpochSecond());

        if (nonNull(validFrom)) {
            builder.putClaim("nbf", validFrom);
        }

        if (nonNull(validUntil)) {
            builder.putClaim("exp", validUntil);
        }

        if (nonNull(vct)) {
            builder.putClaim("vct", vct);
        }

        if (nonNull(statusListIndex)) {
            var statusListReference = new HashMap<String, Object>();
            var innerStatusListReference = new HashMap<>();
            innerStatusListReference.put("idx", statusListIndex);
            innerStatusListReference.put("uri", "https://example.com/statuslists/1");
            statusListReference.put("status_list", innerStatusListReference);
            builder.putClaim("status", statusListReference);
        }

        builder.putClaim("cnf", holderKey.toPublicJWK().toJSONObject());

        try {
            Map<String, Object> claims = builder.build();
            var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .type(new JOSEObjectType("vc+sd-jwt"))
                    .keyID(kidHeaderValue)
                    .build();
            JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            JWSSigner signer = new ECDSASigner(key);
            jwt.sign(signer);

            return new SDJWT(jwt.serialize(), disclosures).toString();
        } catch (ParseException | JOSEException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Create a SD-JWT which has claims which are mandatory to be disclosed as disclosable claims
     */
    private String createIllegalSDJWTMock(String vct, HashMap<String, String> sdClaims) {
        SDObjectBuilder builder = new SDObjectBuilder();
        List<Disclosure> disclosures = new ArrayList<>();

        sdClaims.forEach((k, v) -> {
            Disclosure dis = new Disclosure(k, v);
            builder.putSDClaim(dis);
            disclosures.add(dis);
        });

        // vct & cnf among others have to be always included - they may not be selectively disclosed
        var mandatoryClaims = Map.of(
                "vct", vct,
                "cnf", holderKey.toPublicJWK().toJSONObject()
        );
        mandatoryClaims.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            Disclosure dis = new Disclosure(k, v);
            builder.putSDClaim(dis);
            disclosures.add(dis);
        });

        // issuer will be caught even before getting to selective disclosures.
        builder.putClaim("iss", issuerId);


        builder.putClaim("iat", Instant.now().getEpochSecond());


        try {
            Map<String, Object> claims = builder.build();
            var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .type(new JOSEObjectType("vc+sd-jwt"))
                    .keyID(kidHeaderValue)
                    .build();
            JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            JWSSigner signer = new ECDSASigner(key);
            jwt.sign(signer);

            return new SDJWT(jwt.serialize(), disclosures).toString();
        } catch (ParseException | JOSEException e) {
            throw new AssertionError(e);
        }
    }
}
