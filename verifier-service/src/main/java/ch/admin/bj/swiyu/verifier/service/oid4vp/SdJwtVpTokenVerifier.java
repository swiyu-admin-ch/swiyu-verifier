package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.common.util.json.JsonUtil;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReference;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.*;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

/**
 * Verifies SD-JWT trust statements (which are themselves VP tokens) using the
 * same core verification logic as regular VP tokens, but with trust-specific
 * semantics.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SdJwtVpTokenVerifier {

    // We have vc+sd-jwt only for legacy reasons. We only support sd-jwt vc specification, which uses the format dc+sd-jwt
    public static final List<String> SUPPORTED_CREDENTIAL_FORMATS = List.of("vc+sd-jwt", "dc+sd-jwt");
    public static final List<String> SUPPORTED_JWT_ALGORITHMS = List.of("ES256");

    private static final int MAX_HOLDER_BINDING_AUDIENCES = 1;

    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ApplicationProperties applicationProperties;
    private final VerificationProperties verificationProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SdJwt verifyVpTokenTrustStatement(SdJwt vpToken, Management management) {
        // Re-use the shared verification building blocks
        verifyVerifiableCredentialJWT(vpToken, management);

        if (vpToken.hasKeyBinding()) {
            validateKeyBinding(vpToken, management);
        } else if (canHaveKeyBinding(vpToken.getClaims())) {
            // If the Trust Statement can have a key binding we currently enforce usage of it
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }

        verifyStatus(vpToken.getClaims().getClaims(), management);
        validateDisclosures(vpToken, management);

        return vpToken;
    }

    /**
     * Verifies the given jwt according to basic JWT requirements (header, times, signature).
     *
     * @param sdJwt to be verified, without resolving selective disclosures. Will be updated to have jws header and jwt claims
     */
    protected void verifyVerifiableCredentialJWT(SdJwt sdJwt, Management managementEntity) {
        try {
            SignedJWT nimbusJwt = SignedJWT.parse(sdJwt.getJwt());
            var header = nimbusJwt.getHeader();
            validateHeader(header);
            var claims = nimbusJwt.getJWTClaimsSet();
            // Only technical verification here; issuer trust is validated at service layer
            var publicKey = issuerPublicKeyLoader.loadPublicKey(claims.getIssuer(), header.getKeyID());
            log.trace("Loaded issuer public key for id {}", managementEntity.getId());
            // Verify the JWS signature of the JWT
            if (!nimbusJwt.verify(new DefaultJWSVerifierFactory().createJWSVerifier(header, publicKey))) {
                throw credentialError(MALFORMED_CREDENTIAL, "Signature mismatch");
            }
            log.trace("Successfully verified signature of id {}", managementEntity.getId());
            validateJwtTimes(claims);
            sdJwt.setHeader(header);
            sdJwt.setClaims(claims);
        } catch (ParseException e) {
            throw credentialError(MALFORMED_CREDENTIAL, "Failed to extract information from JWT token");
        } catch (LoadingPublicKeyOfIssuerFailedException | JOSEException e) {
            throw credentialError(e, PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE, e.getMessage());
        }
    }

    /**
     * Update the sdJwts disclosure with validates resolved selective disclosures
     */
    protected void validateDisclosures(SdJwt sdJwt, Management managementEntity) {

        var claims = sdJwt.getClaims();
        var disclosures = sdJwt.getDisclosures();

        // contains all claims except list entries (as they are)
        var disclosedClaimNames = disclosures.stream().map(Disclosure::getClaimName).collect(Collectors.toSet());

        // SD-JWT VC 3.2.2
        if (CollectionUtils.containsAny(disclosedClaimNames, Set.of("iss", "nbf", "exp", "cnf", "vct", "status"))) {
            throw credentialError(MALFORMED_CREDENTIAL, "If present, the following registered JWT claims MUST be included in the SD-JWT and MUST NOT be included in the Disclosures: 'iss', 'nbf', 'exp', 'cnf', 'vct', 'status'");
        }

        // 8.1 / 3.3.3: If the claim name already exists at the level of the _sd key, the SD-JWT MUST be rejected.
        if (CollectionUtils.containsAny(disclosedClaimNames, claims.getClaims().keySet())) { // If there is any result of the set intersection
            throw credentialError(MALFORMED_CREDENTIAL, "Can not resolve disclosures. Existing Claim would be overridden.");
        }

        JsonNode resolvedClaims = processDisclosures(sdJwt.getClaims(), disclosures, managementEntity.getId());

        var prepared = objectMapper.convertValue(resolvedClaims, new TypeReference<Map<String, Object>>(){});

        log.trace("Successfully verified disclosure digests of id {}", managementEntity.getId());

        sdJwt.setResolvedClaims(prepared);
    }

    /**
     * Validates the Header and returns the used encryption Algorithm
     *
     * @param header A header to be validated
     */
    protected void validateHeader(JWSHeader header) {
        if (header.getAlgorithm() == null || !SUPPORTED_JWT_ALGORITHMS.contains(header.getAlgorithm().getName())) {
            throw credentialError(INVALID_FORMAT, "Invalid Algorithm: alg is not supported must be one of %s, but was %s"
                    .formatted(SUPPORTED_JWT_ALGORITHMS, header.getAlgorithm().getName()));
        }
        if (!SUPPORTED_CREDENTIAL_FORMATS.contains(header.getType().getType())) {
            throw credentialError(INVALID_FORMAT, "Type header must be one of %s".formatted(SUPPORTED_CREDENTIAL_FORMATS));
        }
        if (StringUtils.isBlank(header.getKeyID())) {
            throw credentialError(MALFORMED_CREDENTIAL, "Missing header attribute 'kid' for the issuer's Key Id in the JWT token");
        }
    }

    protected void validateJwtTimes(JWTClaimsSet claims) {
        var exp = claims.getExpirationTime();
        if (exp != null && new Date().after(exp)) {
            throw credentialError(JWT_EXPIRED, "Could not verify JWT credential is expired");
        }
        var nbf = claims.getNotBeforeTime();
        if (nbf != null && new Date().before(nbf)) {
            throw credentialError(JWT_PREMATURE, "Could not verify JWT credential is not yet valid");
        }
    }

    void verifyStatus(Map<String, Object> vcClaims, Management managementEntity) {
        statusListReferenceFactory.createStatusListReferences(vcClaims, managementEntity).forEach(StatusListReference::verifyStatus);
    }

    /**
     * 
     * @param claims the claims of a VP Token
     * @return true, if the VP Token is set up to have a key binding
     */
    boolean canHaveKeyBinding(JWTClaimsSet claims) {
        return claims.getClaims().containsKey("cnf");
    }

    void validateKeyBinding(SdJwt sdJwt, Management management) {
        JWK keyBinding = getHolderKeyBinding(sdJwt.getClaims().getClaims());
        // Validate Holder Binding Proof JWT
        JWTClaimsSet keyBindingClaims = getValidatedHolderKeyProof(sdJwt.getKeyBinding().orElseThrow(), keyBinding,
                Optional.ofNullable(management.getConfigurationOverride())
                        .orElse(new ConfigurationOverride(null, null, null, null, null)));
        validateNonce(keyBindingClaims, management.getRequestNonce());
        validateSDHash(sdJwt, keyBindingClaims);
    }

    private void validateSDHash(SdJwt sdjwt, JWTClaimsSet keyBindingClaims) {
        // Compute the SD Hash of the VP Token
        String hash = sdjwt.getPresentationHash();
        String hashClaim = keyBindingClaims.getClaim("sd_hash").toString();
        if (!hash.equals(hashClaim)) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    String.format("Failed to validate key binding. Presented sd_hash '%s' does not match the computed hash '%s'", hashClaim, hash));
        }
    }

    @NotNull
    private JWTClaimsSet getValidatedHolderKeyProof(String keyBindingProof, JWK keyBinding, ConfigurationOverride configurationOverride) {
        JWTClaimsSet keyBindingClaims;
        try {
            SignedJWT keyBindingJWT = SignedJWT.parse(keyBindingProof);

            validateKeyBindingHeader(keyBindingJWT.getHeader());

            if (!keyBindingJWT.verify(new ECDSAVerifier(keyBinding.toECKey()))) {
                throw credentialError(HOLDER_BINDING_MISMATCH, "Holder Binding provided does not match the one in the credential");
            }
            validateKeyBindingClaims(keyBindingJWT);
            keyBindingClaims = keyBindingJWT.getJWTClaimsSet();
            validateHolderBindingAudience(keyBindingClaims.getAudience(), configurationOverride);
        } catch (ParseException e) {
            throw credentialError(e, HOLDER_BINDING_MISMATCH, "Holder Binding could not be parsed");
        } catch (JOSEException e) {
            throw credentialError(e, HOLDER_BINDING_MISMATCH, "Failed to verify the holder key binding - only supporting EC Keys");
        } catch (BadJWTException e) {
            throw credentialError(e, HOLDER_BINDING_MISMATCH, "Holder Binding is not a valid JWT");
        }
        return keyBindingClaims;
    }

    /**
     * Validates if we as verifier are indeed the audience of the holder binding, or whether it was created for another (eg. man in the middle) service.
     *
     * @param audience              the audience as provided in the holder binding JWT
     * @param configurationOverride possible override values
     * @throws VerificationException if the audience for the holder binding does not match the expected one
     */
    private void validateHolderBindingAudience(List<String> audience,
                                               ConfigurationOverride configurationOverride) {
        if (CollectionUtils.isEmpty(audience)) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding audience (aud)");
        }

        /*
         * https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-22.html#section-4.3
         * The value MUST be a single string that identifies the intended receiver of the Key Binding JWT.
         */
        if (audience.size() != MAX_HOLDER_BINDING_AUDIENCES) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    "Multiple audiences not supported. Expected 1 but was %d: %s".formatted(audience.size(), audience));
        }

        String aud = audience.getFirst();
        if (StringUtils.isBlank(aud)) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Audience value is blank");
        }

        String clientId = configurationOverride.verifierDidOrDefault(applicationProperties.getClientId());

        // Exact match only
        if (!clientId.equals(aud)) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    "Holder Binding audience mismatch. Actual: '%s'. Expected: %s"
                            .formatted(aud, clientId));
        }
    }

    /**
     * Check they type and format of the key binding jwt
     */
    private void validateKeyBindingHeader(JWSHeader keyBindingHeader) {
        if (!"kb+jwt".equals(keyBindingHeader.getType().toString())) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    String.format("Type of holder binding typ is expected to be kb+jwt but was %s",
                            keyBindingHeader.getType()));
        }

        if (!SUPPORTED_JWT_ALGORITHMS.contains(keyBindingHeader.getAlgorithm().getName())) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Holder binding algorithm must be in %s".formatted(SUPPORTED_CREDENTIAL_FORMATS));
        }
    }

    /**
     * Check if the jwt has been issued in an acceptable time window
     */
    private void validateKeyBindingClaims(SignedJWT keyBindingJWT) throws BadJWTException, ParseException {
        // See https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens#framework
        new DefaultJWTClaimsVerifier<>(null, Set.of("iat")).verify(keyBindingJWT.getJWTClaimsSet(), null);
        var proofIssueTime = keyBindingJWT.getJWTClaimsSet().getIssueTime().toInstant();
        var now = Instant.now();
        // iat not within acceptable proof time window
        if (proofIssueTime.isBefore(now.minusSeconds(verificationProperties.getAcceptableProofTimeWindowSeconds()))
                || proofIssueTime.isAfter(now.plusSeconds(verificationProperties.getAcceptableProofTimeWindowSeconds()))) {
            throw credentialError(HOLDER_BINDING_MISMATCH, String.format("Holder Binding proof was not issued at an acceptable time. Expected %d +/- %d seconds", now.getEpochSecond(), verificationProperties.getAcceptableProofTimeWindowSeconds()));
        }
    }

    @NotNull
    private JWK getHolderKeyBinding(Map<String, Object> payload) {
        if (!payload.containsKey("cnf")) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "No cnf claim found. Only supporting JWK holder bindings");
        }
        Object keyBindingClaim = payload.get("cnf");
        if (!(keyBindingClaim instanceof Map)) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Holder Binding is not a JWK");
        }

        // Refactor this as soon as issuer and wallet deliver the correct cnf structure
        var jwk = ((Map<?, ?>) keyBindingClaim).get("jwk");
        if (jwk != null) {
            keyBindingClaim = jwk;
        }

        JWK keyBinding;
        try {
            keyBinding = JWK.parse(JsonUtil.getJsonObject(keyBindingClaim));
        } catch (ParseException e) {
            throw credentialError(e, HOLDER_BINDING_MISMATCH, "Holder Binding Key could not be parsed");
        }
        return keyBinding;
    }

    private void validateNonce(JWTClaimsSet keyBindingClaims, String expectedNonce) {
        var actualNonce = keyBindingClaims.getClaim("nonce");
        if (!expectedNonce.equals(actualNonce)) {
            throw credentialError(MISSING_NONCE,
                    String.format("Holder Binding lacks correct nonce expected '%s' but was '%s'", expectedNonce,
                            actualNonce));
        }
    }

    /**
     * Process the Disclosures and embedded digests in the Issuer-signed JWT
     */
    protected JsonNode processDisclosures(JWTClaimsSet claimSet, List<Disclosure> disclosures, UUID managementEntityId) {

        var claims = objectMapper.convertValue(claimSet.getClaims(), JsonNode.class);

        // 3.1 - For each Disclosure provided Calculate the digest over the base64url-encoded string
        Map<String, Disclosure> digestToDisclosure = disclosures.stream().collect(Collectors.toMap(Disclosure::digest, d -> d));

        log.trace("Prepared {} disclosure digests for id {}", disclosures.size(), managementEntityId);

        List<String> usedDigests = new LinkedList<>();

        JsonNode processed = processNode(claims, digestToDisclosure, usedDigests);

        // 3.5 Remove _sd keys
        removeSdKeys(processed);

        // 3.6 Remove _sd_alg
        ((ObjectNode) processed).remove("_sd_alg");

        // 4. Check duplicate digests
        if (usedDigests.size() != new HashSet<>(usedDigests).size()) {
            throw credentialError(MALFORMED_CREDENTIAL, "Duplicate digest detected");
        }

        // 5. Ensure all disclosures used
        if (usedDigests.size() != disclosures.size()) {
            throw credentialError(MALFORMED_CREDENTIAL, "Unused disclosures detected");
        }

        // 6. Validate claims (exp, nbf, aud)
        validateJwtTimes(claimSet);

        return processed;
    }

    private JsonNode processNode(JsonNode node,
                                 Map<String, Disclosure> digestMap,
                                 List<String> usedDigests) {
        if (node.isObject()) {
            return processObjectNode((ObjectNode) node, digestMap, usedDigests);
        }

        if (node.isArray()) {
            return processArrayNode((ArrayNode) node, digestMap, usedDigests);
        }

        return node;
    }

    private JsonNode processObjectNode(ObjectNode object,
                                       Map<String, Disclosure> digestMap,
                                       List<String> usedDigests) {
        // if no _sd key present, just recurse into fields
        if (!object.has("_sd")) {
            Iterator<String> fields = object.fieldNames();
            List<String> names = new ArrayList<>();
            fields.forEachRemaining(names::add);
            for (String name : names) {
                object.set(name, processNode(object.get(name), digestMap, usedDigests));
            }
            return object;
        }

        // object has _sd -> process disclosures first
        ArrayNode sdArray = (ArrayNode) object.get("_sd");

        // snapshot original fields to avoid processing newly added fields
        List<String> originalFields = new ArrayList<>();
        object.fieldNames().forEachRemaining(originalFields::add);

        handleSdArray(object, sdArray, digestMap, usedDigests);

        // iterate only the original fields (skip _sd) and recurse
        for (String field : originalFields) {
            if ("_sd".equals(field)) continue;
            object.set(field, processNode(object.get(field), digestMap, usedDigests));
        }

        return object;
    }

    private void handleSdArray(ObjectNode object,
                               ArrayNode sdArray,
                               Map<String, Disclosure> digestMap,
                               List<String> usedDigests) {
        for (JsonNode digestNode : sdArray) {
            String digest = digestNode.asText();

            if (!digestMap.containsKey(digest)) continue;

            usedDigests.add(digest);
            var disclosure = digestMap.get(digest);
            var claimName = disclosure.getClaimName();

            // 3.2.1 If the contents of the respective Disclosure is not a JSON array of three elements (salt, claim name, claim value), the SD-JWT MUST be rejected.
            if (claimName == null || disclosure.getClaimValue() == null || disclosure.getSalt() == null) {
                throw credentialError(MALFORMED_CREDENTIAL, "Illegal disclosure found");
            }

            // 3.2. If the claim name is _sd or ..., the SD-JWT MUST be rejected.
            if (claimName.equals("_sd") || claimName.equals("...")) {
                throw credentialError(MALFORMED_CREDENTIAL, "Illegal disclosure found with name _sd or ...");
            }

            // 3.3.  If the claim name already exists at the level of the _sd key, the SD-JWT MUST be rejected
            if (object.has(claimName)) {
                throw credentialError(MALFORMED_CREDENTIAL, "Claim name already exists at the level of the _sd key");
            }

            var claimValue = objectMapper.convertValue(disclosure.getClaimValue(), JsonNode.class);
            object.set(claimName, processNode(claimValue, digestMap, usedDigests));
        }
    }

    private JsonNode processArrayNode(ArrayNode array,
                                      Map<String, Disclosure> digestMap,
                                      List<String> usedDigests) {
        ArrayNode newArray = objectMapper.createArrayNode();

        for (JsonNode element : array) {
            if (element.isObject() && element.has("...")) {
                String digest = element.get("...").asText();

                if (!digestMap.containsKey(digest)) continue;

                usedDigests.add(digest);
                var disclosure = digestMap.get(digest);

                if (disclosure.getClaimName() != null || disclosure.getClaimValue() == null || disclosure.getSalt() == null) {
                    throw credentialError(MALFORMED_CREDENTIAL, "Illegal non-array disclosure found");
                }

                var value = objectMapper.convertValue(disclosure.getClaimValue(), JsonNode.class);
                newArray.add(processNode(value, digestMap, usedDigests));
            } else {
                newArray.add(processNode(element, digestMap, usedDigests));
            }
        }

        return newArray;
    }

    private void removeSdKeys(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.remove("_sd");

            Iterator<String> fields = obj.fieldNames();
            while (fields.hasNext()) {
                removeSdKeys(obj.get(fields.next()));
            }
        } else if (node.isArray()) {
            for (JsonNode n : node) {
                removeSdKeys(n);
            }
        }
    }
}