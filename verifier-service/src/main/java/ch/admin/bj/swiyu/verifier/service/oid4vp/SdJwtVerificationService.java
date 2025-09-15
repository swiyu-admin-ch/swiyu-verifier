package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.base64.Base64Utils;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.common.json.JsonUtil;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReference;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.authlete.sd.Disclosure;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SdJwtVerificationService {
    // We have vc+sd-jwt only for legacy reasons. We only support sd-jwt vc specification, which uses the format dc+sd-jwt
    public static final List<String> SUPPORTED_CREDENTIAL_FORMATS = List.of("vc+sd-jwt", "dc+sd-jwt");
    public static final List<String> SUPPORTED_JWT_ALGORITHMS = List.of("ES256");

    private static final int MAX_HOLDER_BINDING_AUDIENCES = 1;

    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties applicationProperties;
    private final VerificationProperties verificationProperties;


    /**
     * Test function to ensure parity with DIF PE
     *
     * @return claims of the vpToken requested in the management as json string
     */
    public String verifyVpTokenPresentationExchange(String vpToken, Management management) {
        var sdJwt = new SdJwt(vpToken);
        sdJwt = verifyVpToken(sdJwt, management);
        try {
            // Presentation Exchange requested format check
            var presentationFormat = sdJwt.getHeader().getType().toString();
            var requestedFormat = getRequestedFormat(presentationFormat, management);
            if (requestedFormat == null || !requestedFormat.alg().contains(sdJwt.getHeader().getAlgorithm().toString())) {
                throw credentialError(INVALID_FORMAT, "Invalid Algorithm: alg must be one of %s, but was %s"
                        .formatted(management.getRequestedPresentation().format().get(presentationFormat).alg(), sdJwt.getHeader().getAlgorithm()));
            }
            var claims = objectMapper.writeValueAsString(sdJwt.getClaims().getClaims());
            checkCommonPresentationDefinitionCriteria(claims, management);
            return claims;
        } catch (JsonProcessingException e) {
            throw credentialError(e, "An error occurred while parsing SDJWT");
        }
    }

    /**
     * Verifies the vpToken according to sd-jwt standard, preparing it for validation with the request query
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
     *
     * @param vpToken    sd-jwt of the vp token to be updated in place
     * @param management verification request management object for ancillary such as nonce and request id
     */
    public SdJwt verifyVpToken(SdJwt vpToken, Management management) {
        // Validate Basic JWT
        verifyVerifiableCredentialJWT(vpToken, management);
        // If Key Binding is present, validate that it is correct
        if (vpToken.hasKeyBinding()) {
            validateKeyBinding(vpToken,
                    management);
        } else if (requiresKeyBinding(vpToken.getClaims())) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }
        verifyStatus(vpToken.getClaims().getClaims(), management);
        // Resolve Disclosures
        validateDisclosures(vpToken, management);

        return vpToken;
    }

    /**
     * Verifies the given jwt according to basic JWT requirements (header, times, signature) and if issuer is trusted
     *
     * @param sdJwt to be verified, without resolving selective disclosures. Will be updated to have jws header and jwt claims
     */
    private void verifyVerifiableCredentialJWT(SdJwt sdJwt, Management managementEntity) {
        try {
            SignedJWT nimbusJwt = SignedJWT.parse(sdJwt.getJwt());
            var header = nimbusJwt.getHeader();
            validateHeader(header);
            var claims = nimbusJwt.getJWTClaimsSet();
            // SWIYU injection ==> We want to ensure we trust the issuer
            validateTrust(claims.getIssuer(), claims.getStringClaim("vct"), managementEntity);
            // We trust the issuer (or everybody)
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
    private void validateDisclosures(SdJwt sdJwt, Management managementEntity) {
        // Step 8 (SD-JWT spec 8.1 / 3 ): Process the Disclosures and embedded digests
        // in the Issuer-signed JWT (section 3 in 8.1)
        List<String> digestsFromDisclosures;
        List<Disclosure> disclosures;
        var claims = sdJwt.getClaims();
        try {
            disclosures = sdJwt.getDisclosures();
            // 8.1 / 3.2.2 If the claim name is _sd or ..., the SD-JWT MUST be rejected.
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

        var updatedClaimsBuilder = new JWTClaimsSet.Builder(claims);
        disclosures.forEach(disclosure -> updatedClaimsBuilder.claim(disclosure.getClaimName(), disclosure.getClaimValue()));
        sdJwt.setClaims(updatedClaimsBuilder.build());
    }

    /**
     * Validates the Header and returns the used encryption Algorithm
     *
     * @param header A header to be validated
     */
    private void validateHeader(JWSHeader header) {

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

    private void validateTrust(String issuerDidTdw, String vct, Management managementEntity) {
        var acceptedIssuerDids = managementEntity.getAcceptedIssuerDids();
        var acceptedIssuersEmpty = acceptedIssuerDids == null || acceptedIssuerDids.isEmpty();
        var trustAnchors = managementEntity.getTrustAnchors();
        var trustAnchorsEmpty = trustAnchors == null || trustAnchors.isEmpty();
        if (acceptedIssuersEmpty && trustAnchorsEmpty) {
            // -> if both, accepted issuers and trust anchors, are not set or empty, all issuers are allowed
            return;
        }
        if (!acceptedIssuersEmpty && acceptedIssuerDids.contains(issuerDidTdw)) {
            // Issuer trusted because it is in the accepted issuer dids
            return;
        }
        if (!trustAnchorsEmpty && hasMatchingTrustStatement(issuerDidTdw, vct, trustAnchors, managementEntity)) {
            return; // We have a valid trust statement for the vct!
        }
        throw credentialError(ISSUER_NOT_ACCEPTED, "Issuer not in list of accepted issuers or connected to trust anchor");
    }

    private boolean hasMatchingTrustStatement(String issuerDidTdw, String vct, List<TrustAnchor> trustAnchors, Management management) {
        if (trustAnchors.stream().anyMatch(trustAnchor -> trustAnchor.did().equals(issuerDidTdw))) {
            return true;
        }

        for (var trustAnchor : trustAnchors) {
            List<String> rawTrustStatementIssuance = fetchTrustStatementIssuance(vct, trustAnchor);
            if (rawTrustStatementIssuance.isEmpty()) {
                // Abort if no trust statement
                log.debug("Failed to get a response for vct {} from {}", vct, trustAnchor.trustRegistryUri());
                continue;
            }

            for (var rawTrustStatement : rawTrustStatementIssuance) {
                try {
                    if (isProvidingTrust(issuerDidTdw, vct, trustAnchor, rawTrustStatement, management))
                        return true;
                } catch (VerificationException e) {
                    // This exception will occur if the trust statement can not be verified fully
                    log.debug("Failed to verify trust statement for vct {} from {} with code {} due to {}", vct, trustAnchor.trustRegistryUri(), e.getErrorResponseCode(), e.getErrorDescription());
                } catch (ParseException e) {
                    log.info("Trust Statement of %s is malformed - missing CanIssue claim");
                }
            }
        }
        return false;
    }

    private boolean isProvidingTrust(String issuerDidTdw, String vct, TrustAnchor trustAnchor, String rawTrustStatement, Management management) throws ParseException {
        var trustStatement = new SdJwt(rawTrustStatement);
        trustStatement = verifyVpToken(trustStatement, management);
        return issuerDidTdw.equals(trustStatement.getClaims().getSubject())
                && trustAnchor.did().equals(trustStatement.getClaims().getIssuer())
                && vct.equals(trustStatement.getClaims().getStringClaim("canIssue"));
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

    private void verifyStatus(Map<String, Object> vcClaims, Management managementEntity) {
        statusListReferenceFactory.createStatusListReferences(vcClaims, managementEntity).forEach(StatusListReference::verifyStatus);
    }

    private boolean requiresKeyBinding(JWTClaimsSet claims) {
        return claims.getClaims().containsKey("cnf");
    }

    private void validateKeyBinding(SdJwt sdJwt, Management management) {
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
        String presentation = sdjwt.getPresentation();
        String hash;
        try {
            var hashDigest = MessageDigest.getInstance("sha-256").digest(presentation.getBytes());
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


    private void validateHolderBindingAudience(List<String> audience,
                                               ConfigurationOverride configurationOverride) {
        if (CollectionUtils.isEmpty(audience)){
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

        String aud = audience.get(0);
        if (aud == null || aud.isBlank()) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Audience value is blank");
        }

        String effectiveVerifierDid = Optional.ofNullable(configurationOverride.verifierDid())
                .orElse(applicationProperties.getClientId());

        String effectiveExternalUrl = Optional.ofNullable(configurationOverride.externalUrl())
                .orElse(applicationProperties.getExternalUrl());

        // Normalize external URL (remove trailing slash)
        if (effectiveExternalUrl != null && effectiveExternalUrl.endsWith("/")) {
            effectiveExternalUrl = effectiveExternalUrl.substring(0, effectiveExternalUrl.length() - 1);
        }

        // Allowed exact audiences (spec: single string identifying the receiver)
        Set<String> allowedAudiences = new HashSet<>();
        if (effectiveVerifierDid != null) {
            allowedAudiences.add(effectiveVerifierDid);
        }
        if (effectiveExternalUrl != null) {
            allowedAudiences.add(effectiveExternalUrl);
        }

        // Exact match only
        if (!allowedAudiences.contains(aud)) {
            throw credentialError(HOLDER_BINDING_MISMATCH,
                    "Holder Binding audience mismatch. Actual: '%s'. Expected one of: %s"
                            .formatted(aud, allowedAudiences));
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
}
