package ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock;

import ch.admin.bj.swiyu.verifier.dto.submission.DescriptorDto;
import ch.admin.bj.swiyu.verifier.dto.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;

import static java.util.Objects.nonNull;

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

    public static SDObjectBuilder getClaimsFromSdJwt(List<Disclosure> disclosure) {
        SDObjectBuilder builder = new SDObjectBuilder();

        var nameDisc = new Disclosure("name", "Max Muster");
        builder.putSDClaim(nameDisc);
        disclosure.add(nameDisc);

        // ---------- Address 1 ----------
        SDObjectBuilder address1 = new SDObjectBuilder();
        var address1Map = Map.of("city", "Bern", "street", "Bahnhofstrasse", "house_number", 1, "country", "CH");
        address1Map.forEach((claimName, claimValue) -> {
            var disc = new Disclosure(claimName, claimValue);
            address1.putSDClaim(disc);
            disclosure.add(disc);
        });
        var addressDisc1 = new Disclosure(address1.build());
        disclosure.add(addressDisc1);

        // ---------- Address 2 ----------
        SDObjectBuilder address2 = new SDObjectBuilder();
        var address2Map = Map.of("city", "Zürich", "street", "Bahnhofstrasse", "house_number", 10, "country", "CH");
        address2Map.forEach((claimName, claimValue) -> {
            var disc = new Disclosure(claimName, claimValue);
            address2.putSDClaim(disc);
            disclosure.add(disc);
        });
        var addressDisc2 = new Disclosure(address2.build());
        disclosure.add(addressDisc2);

        var addressList1 = addressDisc1.toArrayElement();
        var addressList2 = addressDisc2.toArrayElement();
        var addressesDisc = new Disclosure("addresses", List.of(addressList1, addressList2));
        builder.putSDClaim(addressesDisc);
        disclosure.add(addressesDisc);

        var emailDisc = new Disclosure("email", "max@example.com");
        builder.putSDClaim(emailDisc);
        disclosure.add(emailDisc);

        return builder;
    }

    public String createSDJWTMockRecursiveObject(Long validFrom, Long validUntil, Integer statusListIndex, String vct, boolean useLegacyCnfFormat, String credentialFormat, JWSAlgorithm jwsAlgorithm, boolean skipCnf) {

        SDObjectBuilder builder = new SDObjectBuilder();
        List<Disclosure> disclosures = new ArrayList<>();

        Map<String, Object> address = Map.of("street", "Bhf str.", "country", "CH", "zip", "8000");
        Map<String, Object> address2 = Map.of("street", "Bhf str.", "country", "CH", "zip", "3000");

        List<Map<String, Object>> addressList = List.of(address, address2);

        var addresses = addressList.stream().map(addr -> {
            var addressBuilder = new SDObjectBuilder();
            addr.forEach((key1, value) -> {
                var dis = new Disclosure(key1, value);
                disclosures.add(dis);
                addressBuilder.putSDClaim(dis);
            });
            var addressDisclosure = new Disclosure(addressBuilder.build());
            disclosures.add(addressDisclosure);
            return addressDisclosure.toArrayElement();
        }).toList();

        var addressDisclosure = new Disclosure("addresses", addresses);
        disclosures.add(addressDisclosure);
        var companyBuilder = new SDObjectBuilder();
        companyBuilder.putSDClaim(addressDisclosure);
        var nameDisc = new Disclosure("name", "Max Muster AG");
        companyBuilder.putSDClaim(nameDisc);
        disclosures.add(nameDisc);
        var companyDisclosure = new Disclosure("company", companyBuilder.build());
        builder.putSDClaim(companyDisclosure);
        disclosures.add(companyDisclosure);

        return createSdJWT(builder, disclosures, validFrom, validUntil, statusListIndex, vct, useLegacyCnfFormat, credentialFormat, jwsAlgorithm, skipCnf);
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

    public String createSDJWTMock(boolean useLegacyCnfFormat, String credentialFormat) {
        return createSDJWTMock(null, null, null, DEFAULT_VCT, getSDClaims(), useLegacyCnfFormat, credentialFormat, false);
    }

    public String createSDJWTMock(boolean skipCnf) {
        return createSDJWTMock(null, null, null, DEFAULT_VCT, getSDClaims(), false, "dc+sd-jwt", skipCnf);
    }

    public String createSDJWTMockWithRecursiveListArray() {
        return createSDJWTMockRecursiveObject(null, null, null, DEFAULT_VCT, false, "dc+sd-jwt", JWSAlgorithm.ES256, false);
    }

    public String createSDJWTMockWithClaims(Map<String, Object> sdClaims) {
        return createSDJWTMock(null, null, null, DEFAULT_VCT, sdClaims, false, "vc+sd-jwt", false);
    }

    public String createTrustStatementIssuanceV1(String trustStatementIssuerDid, String trustStatementIssuerKeyId) throws JOSEException {
        return createTrustStatementIssuanceV1(trustStatementIssuerDid, trustStatementIssuerKeyId, this.issuerId);
    }

    /**
     * Create default Trust Statement for the VC the emulator creates
     * TODO Add link to trust protocol v1.0 once it is published
     */
    public String createTrustStatementIssuanceV1(String trustStatementIssuerDid, String trustStatementIssuerKeyId, String trustedIssuer) throws JOSEException {
        // While being an SD-JWT VC the TrustStatementIssuanceV1 has only ALWAYS disclosed claims
        var claims = new JWTClaimsSet.Builder()
                .issuer(trustStatementIssuerDid)
                .subject(trustedIssuer)
                .claim("vct", "TrustStatementIssuanceV1")
                .claim("canIssue", DEFAULT_VCT)
                .build();
        var headers = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(trustStatementIssuerKeyId)
                .type(new JOSEObjectType("vc+sd-jwt"))
                .build();
        SignedJWT jwt = new SignedJWT(headers, claims);
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize()+"~";
    }

    /**
     * Adds a key binding proof the SD-JWT.
     *
     * @param sdjwt a string {jwt}~{disclosure-1}~...{disclosure-n}~
     * @return complete sdjwt with key binding as a string {jwt}~{disclosure-1}~...{disclosure-n}~{keyBindingProof}
     */
    public String addKeyBindingProof(String sdjwt, String nonce, String aud) throws NoSuchAlgorithmException, ParseException, JOSEException {
        return addKeyBindingProof(sdjwt, nonce, aud, Instant.now().getEpochSecond(), "kb+jwt");
    }

    public String addKeyBindingProof(String sdjwt, String nonce, String aud, long iat, String format) throws NoSuchAlgorithmException, ParseException, JOSEException {
        // Create hash, hope not to have any indigestion
        var hash = new String(Base64.getUrlEncoder().withoutPadding().encode(MessageDigest.getInstance("sha-256").digest(sdjwt.getBytes())));
        HashMap<String, Object> proofData = new HashMap<>();
        proofData.put("sd_hash", hash);
        proofData.put("iat", iat);
        proofData.put("aud", aud);
        proofData.put("nonce", nonce);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType(format)).build();
        var jwt = new SignedJWT(header, JWTClaimsSet.parse(proofData));
        jwt.sign(new ECDSASigner(holderKey));
        return sdjwt + jwt.serialize();
    }

    public static HashMap<String, Object> getSDClaims() {
        HashMap<String, Object> claims = new HashMap<>();

        claims.put("first_name", "TestFirstname");
        claims.put("last_name", "TestLastName");
        claims.put("birthdate", "1949-01-22");
        claims.put("languages", List.of("DE", "FR", "IT"));

        return claims;
    }

    private String createSDJWTMock(Long validFrom, Long validUntil, Integer statusListIndex, String vct, HashMap<String, Object> sdClaims) {
        return createSDJWTMock(validFrom, validUntil, statusListIndex, vct, sdClaims, false, "vc+sd-jwt", false);
    }

    // Refactor this as soon as issuer and wallet deliver the correct cnf structure
    private String createSDJWTMock(Long validFrom, Long validUntil, Integer statusListIndex, String vct, Map<String, Object> sdClaims, boolean useLegacyCnfFormat, String credentialFormat, boolean skipCnf) {
        SDObjectBuilder builder = new SDObjectBuilder();
        List<Disclosure> disclosures = new ArrayList<>();

        sdClaims.forEach((k, v) -> {

            if (v instanceof Collection<?> values) {
                var disc = values.stream().map(item -> {
                    var dis = new Disclosure(item);
                    disclosures.add(dis);
                    return dis.toArrayElement();
                }).toList();

                builder.putClaim(k, disc);
            } else {
                Disclosure dis = new Disclosure(k, v);
                builder.putSDClaim(dis);
                disclosures.add(dis);
            }
        });

        return createSdJWT(builder, disclosures, validFrom, validUntil, statusListIndex, vct,  useLegacyCnfFormat, credentialFormat, JWSAlgorithm.ES256, skipCnf);
    }

    public String createNestedSDJWTMock() {

        List<Disclosure> disclosures = new ArrayList<>();

        var builder = getClaimsFromSdJwt(disclosures);

        return createSdJWT(builder, disclosures, null, null, null, DEFAULT_VCT, false, "vc+sd-jwt", JWSAlgorithm.ES256, false);
    }

    public String createSdJWT(SDObjectBuilder builder, List<Disclosure> disclosures, Long validFrom, Long validUntil, Integer statusListIndex, String vct, boolean useLegacyCnfFormat, String credentialFormat, JWSAlgorithm jwsAlgorithm, boolean skipCnf) {
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

        // Refactor this as soon as issuer and wallet deliver the correct cnf structure
        if (!skipCnf) {
            if (useLegacyCnfFormat) {
                builder.putClaim("cnf", holderKey.toPublicJWK().toJSONObject());
            } else {
                Map<String, Object> correctCNFClaim = new HashMap<>();
                correctCNFClaim.put("jwk", holderKey.toPublicJWK().toJSONObject());
                builder.putClaim("cnf", correctCNFClaim);
            }
        }

        try {
            Map<String, Object> claims = builder.build();
            var header = new JWSHeader.Builder(jwsAlgorithm)
                    .type(new JOSEObjectType(credentialFormat))
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