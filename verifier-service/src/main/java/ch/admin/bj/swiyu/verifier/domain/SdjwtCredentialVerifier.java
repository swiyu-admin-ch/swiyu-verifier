package ch.admin.bj.swiyu.verifier.domain;

import ch.admin.bj.swiyu.verifier.common.util.base64.Base64Utils;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.common.util.json.JsonUtil;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReference;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDObjectDecoder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.*;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;
import static ch.admin.bj.swiyu.verifier.domain.CredentialVerifierSupport.checkCommonPresentationDefinitionCriteria;
import static ch.admin.bj.swiyu.verifier.domain.CredentialVerifierSupport.getRequestedFormat;

/**
 * Verifies the presentation of a SD-JWT Credential.
 *
 * @deprecated: class is deprecated and will be removed in future versions when all OID4VP verifications use the new adapter-based approach.
 */
@AllArgsConstructor
@Slf4j
@Deprecated(since="OID4VP 1.0")
public class SdjwtCredentialVerifier {

    public static final String CREDENTIAL_FORMAT = "vc+sd-jwt";
    public static final String CREDENTIAL_FORMAT_NEW = "dc+sd-jwt";
    public static final List<String> SUPPORTED_CREDENTIAL_FORMATS = List.of(CREDENTIAL_FORMAT, CREDENTIAL_FORMAT_NEW);

    private final List<String> supportedAlgorithms = List.of("ES256");
    private final String vpToken;
    private final Management managementEntity;
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;
    private final VerificationProperties verificationProperties;
    private final ApplicationProperties applicationProperties;
    private static final int MAX_HOLDER_BINDING_AUDIENCES = 1;


    /**
     * Verifies the presentation of a SD-JWT Credential as described in
     * <p>
     * <a href=
     * "https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-22.html">Selective
     * Disclosure for JWTs (SD-JWT)</a>
     * <ul>
     * <li>
     * <a href=
     * "https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-22.html#name-verification-of-the-sd-jwt">
     * 7.1 Verification of the SD-JWT
     * </a>
     * </li>
     * <li>
     * <a href=
     * "https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-22.html#section-7.3">
     * 7.3 Verification by the Verifier
     * </a>
     * </li>
     * <li>
     *     <a href=https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-08.html#section-3.2.2.2>SD-JWT VC 3.2</a>
     * </li>
     * </ul>
     * .
     *
     * @return the verified SD-JWT as string.
     */
    public String verifyPresentation() {

        SdJwtData result = verifySdJwt(vpToken);

        // Confirm that the returned Credential(s) meet all criteria sent in the
        // Presentation Definition in the Authorization Request.
        var sdjwt = checkPresentationDefinitionCriteria(result.payload.getClaims(), result.disclosures());

        // The data submission is valid, we can now begin to check the status of the VC
        verifyStatus(result.payload().getClaims());

        log.trace("Successfully verified the presented VC for id {}", managementEntity.getId());
        return sdjwt;
    }

    /**
     * Verifies a SD-JWT Credential as described in
     * <p>
     * <a href=
     * "https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-22.html">Selective
     * Disclosure for JWTs (SD-JWT)</a>
     * <ul>
     * <li>
     * <a href=
     * "https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-22.html#name-verification-of-the-sd-jwt">
     * 7.1 Verification of the SD-JWT
     * </a>
     * </li>
     * </ul>
     */
    @NotNull
    private SdJwtData verifySdJwt(String fullSdJwt) {

        SdJwt sdJwt = new SdJwt(fullSdJwt);
        String[] parts = sdJwt.getParts();

        var claims = validateJwtClaims(parts[0]);

        // Step 7 (SD-JWT spec 8.3): Key Binding Verification
        int disclosureLength = validateHolderBinding(sdJwt, claims, parts);

        List<Disclosure> disclosures = getValidatedDisclosures(parts, disclosureLength, claims);

        return new SdJwtData(claims, disclosures);
    }

    @NotNull
    private List<Disclosure> getValidatedDisclosures(String[] parts, int disclosureLength, JWTClaimsSet claims) {
        // Step 8 (SD-JWT spec 8.1 / 3 ): Process the Disclosures and embedded digests
        // in the Issuer-signed JWT (section 3 in 8.1)
        List<Disclosure> disclosures;
        List<String> digestsFromDisclosures;

        // 8.1 / 3.2.2 If the claim name is _sd or ..., the SD-JWT MUST be rejected.
        try {
            disclosures = Arrays.stream(Arrays.copyOfRange(parts, 1, disclosureLength))
                    .map(Disclosure::parse).toList();
            digestsFromDisclosures = disclosures.stream().map(Disclosure::digest).toList();
        } catch (IllegalArgumentException e) {
            throw credentialError(MALFORMED_CREDENTIAL, "Illegal disclosure found with name _sd or ...");
        }
        log.trace("Prepared {} disclosure digests for id {}", disclosures.size(), managementEntity.getId());

        var disclosedClaimNames = disclosures.stream().map(Disclosure::getClaimName).collect(Collectors.toSet());

        // SD-JWT VC 3.2.2
        if (CollectionUtils.containsAny(disclosedClaimNames, Set.of("iss", "nbf", "exp", "cnf", "vct", "status"))) {
            throw credentialError(MALFORMED_CREDENTIAL, "If present, the following registered JWT claims MUST be included in the SD-JWT and MUST NOT be included in the Disclosures: 'iss', 'nbf', 'exp', 'cnf', 'vct', 'status'");
        }

        // 8.1 / 3.3.3: If the claim name already exists at the level of the _sd key, the SD-JWT MUST be rejected.
        if (CollectionUtils.containsAny(disclosedClaimNames, claims.getClaims().keySet())) { // If there is any result of the set intersection
            throw credentialError(MALFORMED_CREDENTIAL, "Can not resolve disclosures. Existing Claim would be overridden.");
        }


        // check if distinct disclosures
        if (new HashSet<>(disclosures).size() != disclosures.size()) {
            throw credentialError(MALFORMED_CREDENTIAL,
                    "Request contains non-distinct disclosures");
        }

        // If any digest is missing or appears more than once in _sd, the check fails. This enforces that all disclosed digests are uniquely present in the _sd claim, as required by the SD-JWT specification.
        if (!digestsFromDisclosures.stream()
                .allMatch(dig -> {
                    try {
                        return Collections.frequency(claims.getListClaim("_sd"), dig) == 1;
                    } catch (ParseException e) {
                        throw credentialError(MALFORMED_CREDENTIAL,
                                "Could not verify JWT. _sd field was not a list of disclosures.");
                    }
                })) {
            throw credentialError(MALFORMED_CREDENTIAL,
                    "Could not verify JWT problem with disclosures and _sd field");
        }
        log.trace("Successfully verified disclosure digests of id {}", managementEntity.getId());
        return disclosures;
    }

    /**
     * Validates the holder binding. From this we will also know how many disclosures there are.
     */
    private int validateHolderBinding(SdJwt sdJwt, JWTClaimsSet claims, String... parts) {
        var payload = claims.getClaims();
        int disclosureLength = parts.length;

        boolean keyBindingProofPresent = sdJwt.hasKeyBinding();
        if (payload.containsKey("cnf") && !keyBindingProofPresent) {
            // There is a Holder Key Binding, but we did not receive a proof for it!
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }

        if (keyBindingProofPresent) {
            disclosureLength -= 1;
            log.trace("Verifying holder keybinding of id {}", managementEntity.getId());
            validateKeyBinding(payload, parts[parts.length - 1]);
            log.trace("Successfully verified holder keybinding of id {}", managementEntity.getId());
        }
        return disclosureLength;
    }

    private JWTClaimsSet validateJwtClaims(String jwt) {
        try {
            SignedJWT nimbusJwt = SignedJWT.parse(jwt);
            var header = nimbusJwt.getHeader();
            validateHeader(header);
            var claims = nimbusJwt.getJWTClaimsSet();
            // SWIYU injection ==> We want to ensure we trust the issuer
            validateTrust(claims.getIssuer(), claims.getStringClaim("vct"));
            // We trust the issuer (or everybody)
            var publicKey = issuerPublicKeyLoader.loadPublicKey(claims.getIssuer(), header.getKeyID());
            log.trace("Loaded issuer public key for id {}", managementEntity.getId());
            // Verify the JWS signature of the JWT
            if (!nimbusJwt.verify(new DefaultJWSVerifierFactory().createJWSVerifier(header, publicKey))) {
                throw credentialError(MALFORMED_CREDENTIAL, "Signature mismatch");
            }
            log.trace("Successfully verified signature of id {}", managementEntity.getId());
            validateJwtTimes(claims);
            return claims;
        } catch (ParseException e) {
            throw credentialError(MALFORMED_CREDENTIAL, "Failed to extract information from JWT token");
        } catch (LoadingPublicKeyOfIssuerFailedException | JOSEException e) {
            throw credentialError(e, PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE, e.getMessage());
        }
    }

    private void validateJwtTimes(JWTClaimsSet claims) {
        var exp = claims.getExpirationTime();
        if (exp != null && new Date().after(exp)) {
            throw credentialError(JWT_EXPIRED, "Could not verify JWT credential is expired");
        }
        var nbf = claims.getNotBeforeTime();
        if (nbf != null && new Date().before(nbf)) {
            throw credentialError(JWT_PREMATURE, "Could not verify JWT credential is not yet valid");
        }
    }

    /**
     * Validates the Header and returns the used encryption Algorithm
     *
     * @param header A header to be validated
     */
    private void validateHeader(JWSHeader header) {
        var requestedAlg = getRequestedFormat(CREDENTIAL_FORMAT, managementEntity).alg();
        if (header.getAlgorithm() == null || !requestedAlg.contains(header.getAlgorithm().getName())) {
            throw credentialError(INVALID_FORMAT, "Invalid Algorithm: alg must be one of %s, but was %s"
                    .formatted(requestedAlg, header.getAlgorithm().getName()));
        }
        if (!SUPPORTED_CREDENTIAL_FORMATS.contains(header.getType().getType())) {
            throw credentialError(INVALID_FORMAT, String.format("Type header must be %s or %s", CREDENTIAL_FORMAT, CREDENTIAL_FORMAT_NEW));
        }
        // TODO Move the supported algorithm check to creation of the verification request
        if (!supportedAlgorithms.contains(header.getAlgorithm().getName())) {
            throw credentialError(UNSUPPORTED_FORMAT, "Unsupported algorithm: " + header.getAlgorithm());
        }
        if (StringUtils.isBlank(header.getKeyID())) {
            throw credentialError(MALFORMED_CREDENTIAL, "Missing header attribute 'kid' for the issuer's Key Id in the JWT token");
        }
    }

    private void validateTrust(String issuerDid, String vct) {
        var acceptedIssuerDids = managementEntity.getAcceptedIssuerDids();
        var acceptedIssuersEmpty = acceptedIssuerDids == null || acceptedIssuerDids.isEmpty();
        var trustAnchors = managementEntity.getTrustAnchors();
        var trustAnchorsEmpty = trustAnchors == null || trustAnchors.isEmpty();
        if (acceptedIssuersEmpty && trustAnchorsEmpty) {
            // -> if both, accepted issuers and trust anchors, are not set or empty, all issuers are allowed
            return;
        }

        if (!acceptedIssuersEmpty && acceptedIssuerDids.contains(issuerDid)) {
            // Issuer trusted because it is in the accepted issuer dids
            return;
        }

        if (!trustAnchorsEmpty && hasMatchingTrustStatement(issuerDid, vct, trustAnchors)) {
            return; // We have a valid trust statement for the vct!
        }

        throw credentialError(ISSUER_NOT_ACCEPTED, "Issuer not in list of accepted issuers or connected to trust anchor");
    }

    /**
     * Checks if the issuer has a valid trust statement from any of the provided trust anchors.
     * Tries direct match first (issuer DID = trust anchor DID), then queries trust registries.
     *
     * @param issuerDid    the DID of the issuer to verify
     * @param vct          the Verifiable Credential Type
     * @param trustAnchors the list of trust anchors to check
     * @return true if a matching trust statement is found, false otherwise
     */
    private boolean hasMatchingTrustStatement(String issuerDid, String vct, List<TrustAnchor> trustAnchors) {
        // Direct trust: issuer DID matches a trust anchor DID
        if (isDirectlyTrustedIssuer(issuerDid, trustAnchors)) {
            return true;
        }

        // Indirect trust: issuer is vouched for by a trust anchor via trust statement
        return isTrustedViaRegistry(issuerDid, vct, trustAnchors);
    }

    /**
     * Checks if the issuer DID directly matches any trust anchor DID.
     */
    private boolean isDirectlyTrustedIssuer(String issuerDid, List<TrustAnchor> trustAnchors) {
        return trustAnchors.stream().anyMatch(trustAnchor -> trustAnchor.did().equals(issuerDid));
    }

    /**
     * Queries trust anchors for trust statements that vouch for the issuer.
     * For each anchor, fetches trust statements for the VCT and validates them.
     */
    private boolean isTrustedViaRegistry(String issuerDid, String vct, List<TrustAnchor> trustAnchors) {
        for (var trustAnchor : trustAnchors) {
            List<String> trustStatements = fetchTrustStatementIssuance(vct, trustAnchor);
            if (trustStatements.isEmpty()) {
                log.debug("Failed to fetch trust statements for vct {} from {}", vct, trustAnchor.trustRegistryUri());
                continue;
            }

            if (verifyTrustStatements(issuerDid, vct, trustAnchor, trustStatements)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates a collection of trust statements to find one that vouches for the issuer.
     */
    private boolean verifyTrustStatements(String issuerDid, String vct, TrustAnchor trustAnchor,
                                          List<String> trustStatements) {
        for (var rawTrustStatement : trustStatements) {
            if (validateTrustStatement(issuerDid, vct, trustAnchor, rawTrustStatement)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates a single trust statement with error handling.
     * Returns false on verification or parsing errors, allowing iteration to continue.
     */
    private boolean validateTrustStatement(String issuerDid, String vct, TrustAnchor trustAnchor,
                                           String rawTrustStatement) {
        try {
            return isProvidingTrust(issuerDid, vct, trustAnchor, rawTrustStatement);
        } catch (VerificationException e) {
            log.debug("Failed to verify trust statement for vct {} from {} - {}: {}",
                    vct, trustAnchor.trustRegistryUri(), e.getErrorResponseCode(), e.getErrorDescription());
            return false;
        } catch (ParseException e) {
            log.info("Trust statement is malformed - missing canIssue claim");
            return false;
        }
    }

    private boolean isProvidingTrust(String issuerDid, String vct, TrustAnchor trustAnchor, String rawTrustStatement) throws ParseException {
        var trustStatementIssuance = verifySdJwt(rawTrustStatement);
        return issuerDid.equals(trustStatementIssuance.payload.getSubject())
                && trustAnchor.did().equals(trustStatementIssuance.payload.getIssuer())
                && vct.equals(trustStatementIssuance.payload.getStringClaim("canIssue"));
    }

    @NotNull
    private List<String> fetchTrustStatementIssuance(String vct, TrustAnchor trustAnchor) {
        if (StringUtils.isBlank(vct)) {
            return List.of();
        }
        List<String> rawTrustStatementIssuance;
        try {
            rawTrustStatementIssuance = issuerPublicKeyLoader.loadTrustStatement(trustAnchor.trustRegistryUri(), vct);
        } catch (JsonProcessingException e) {
            return List.of();
        }
        return rawTrustStatementIssuance;
    }

    // Note: this method is package-private because this method is used in unit
    // tests, otherwise it could be private
    public String checkPresentationDefinitionCriteria(Map<String, Object> claims, List<Disclosure> disclosures)
            throws VerificationException {
        Map<String, Object> expectedMap = new HashMap<>(claims);
        SDObjectDecoder decoder = new SDObjectDecoder();
        String sdJWTString;

        try {
            Map<String, Object> decodedSDJWT = decoder.decode(expectedMap, disclosures);
            log.trace("Decoded SD-JWT to clear data for id {}", managementEntity.getId());
            sdJWTString = objectMapper.writeValueAsString(decodedSDJWT);
        } catch (JsonProcessingException e) {
            throw credentialError(e, "An error occurred while parsing SDJWT");
        }
        log.trace("Checking presentation data with definition criteria for id {}", managementEntity.getId());
        checkCommonPresentationDefinitionCriteria(sdJWTString, managementEntity);

        return sdJWTString;
    }

    private void verifyStatus(Map<String, Object> vcClaims) {
        statusListReferenceFactory.createStatusListReferences(vcClaims, managementEntity).forEach(StatusListReference::verifyStatus);
    }

    private void validateKeyBinding(Map<String, Object> payload, String keyBindingProof) {
        JWK keyBinding = getHolderKeyBinding(payload);
        // Validate Holder Binding Proof JWT

        JWTClaimsSet keyBindingClaims = getValidatedHolderKeyProof(keyBindingProof, keyBinding);
        validateNonce(keyBindingClaims);
        validateSDHash(keyBindingClaims);
        validateKeyBindingAudience(keyBindingClaims.getAudience());
    }

    private void validateKeyBindingAudience(List<String> audience) {
        if (audience == null || audience.isEmpty()) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding audience (aud)");
        }
        /*
         * https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-22.html#section-4.3
         * The value MUST be a single string that identifies the intended receiver of the Key Binding JWT.
         */
        if (audience.size() != MAX_HOLDER_BINDING_AUDIENCES) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    "Multiple audiences not supported. Expected 1 but was " + audience.size());
        }

        String aud = audience.get(0);
        if (aud == null || aud.isBlank()) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Audience value is blank");
        }

        var override = managementEntity.getConfigurationOverride();
        String clientId = Optional.ofNullable(override.verifierDid())
                .orElse(applicationProperties.getClientId());


        // Audience MUST be client_id
        // See: https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-14.1.2

        Set<String> allowed = new HashSet<>();
        if (clientId != null) allowed.add(clientId);

        if (!allowed.contains(aud)) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    "Holder Binding audience mismatch. Actual: '" + aud + "' Expected one of: " + allowed);
        }
    }


    private void validateSDHash(JWTClaimsSet keyBindingClaims) {
        // Compute the SD Hash of the VP Token
        String sdjwt = vpToken.substring(0, vpToken.lastIndexOf(SdJwt.JWT_PART_DELINEATION_CHARACTER) + 1);
        String hash;
        try {
            var hashDigest = MessageDigest.getInstance("sha-256").digest(sdjwt.getBytes());
            hash = Base64Utils.encodeBase64(hashDigest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to validate key binding. Loading hash algorithm failed. Please check the configuration.", e);
        }
        String hashClaim = keyBindingClaims.getClaim("sd_hash").toString();
        if (!hash.equals(hashClaim)) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    String.format("Failed to validate key binding. Presented sd_hash '%s' does not match the computed hash '%s'", hashClaim, hash));
        }
    }

    @NotNull
    private JWTClaimsSet getValidatedHolderKeyProof(String keyBindingProof, JWK keyBinding) {
        JWTClaimsSet keyBindingClaims;
        try {
            SignedJWT keyBindingJWT = SignedJWT.parse(keyBindingProof);

            validateKeyBindingHeader(keyBindingJWT.getHeader());

            if (!keyBindingJWT.verify(new ECDSAVerifier(keyBinding.toECKey()))) {
                throw credentialError(HOLDER_BINDING_MISMATCH, "Holder Binding provided does not match the one in the credential");
            }
            validateKeyBindingClaims(keyBindingJWT);
            keyBindingClaims = keyBindingJWT.getJWTClaimsSet();
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
     * Check they type and format of the key binding jwt
     */
    private void validateKeyBindingHeader(JWSHeader keyBindingHeader) {
        if (!"kb+jwt".equals(keyBindingHeader.getType().toString())) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    String.format("Type of holder binding typ is expected to be kb+jwt but was %s",
                            keyBindingHeader.getType()));
        }

        // Check if kb algorithm matches the required format
        var requestedKeyBindingAlg = getRequestedFormat(CREDENTIAL_FORMAT, managementEntity).keyBindingAlg();
        if (!requestedKeyBindingAlg.contains(keyBindingHeader.getAlgorithm().getName())) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Holder binding algorithm must be in %s".formatted(requestedKeyBindingAlg));
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

    private void validateNonce(JWTClaimsSet keyBindingClaims) {
        var expectedNonce = managementEntity.getRequestNonce();
        var actualNonce = keyBindingClaims.getClaim("nonce");
        if (!expectedNonce.equals(actualNonce)) {
            throw credentialError(MISSING_NONCE,
                    String.format("Holder Binding lacks correct nonce expected '%s' but was '%s'", expectedNonce,
                            actualNonce));
        }
    }

    private record SdJwtData(JWTClaimsSet payload, List<Disclosure> disclosures) {
    }
}

