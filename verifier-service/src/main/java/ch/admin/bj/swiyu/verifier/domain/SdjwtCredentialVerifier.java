/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.domain;

import ch.admin.bj.swiyu.verifier.common.base64.Base64Utils;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.common.json.JsonUtil;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
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
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import io.jsonwebtoken.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
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
 */
@AllArgsConstructor
@Slf4j
public class SdjwtCredentialVerifier {

    public static final String CREDENTIAL_FORMAT = "vc+sd-jwt";

    private final List<String> supportedAlgorithms = List.of("ES256");
    private final String vpToken;
    private final Management managementEntity;
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;
    private final VerificationProperties verificationProperties;


    /**
     * Verifies the presentation of a SD-JWT Credential as described in
     * <p>
     * <a href=
     * "https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-10.html">Selective
     * Disclosure for JWTs (SD-JWT)</a>
     * <ul>
     * <li>
     * <a href=
     * "https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-10.html#name-verification-of-the-sd-jwt">
     * 8.1 Verification of the SD-JWT
     * </a>
     * </li>
     * <li>
     * <a href=
     * "https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-10.html#section-8.3">
     * 8.3 Verification by the Verifier
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

        // Step 1 (SD-JWT spec 8.1 / 1): Separate the SD-JWT into the Issuer-signed JWT
        // and the Disclosures (if any).
        String[] parts = vpToken.split("~");
        var issuerSignedJWTToken = parts[0];

        // Step 2: Check if issuer is accepted or trusted
        var issuerDidTdw = extractIssuer(issuerSignedJWTToken);
        validateTrust(issuerDidTdw);

        // Step 3 (SD-JWT spec 8.1 / 2.3): validate that the signing key belongs to the
        // Issuer ...
        PublicKey publicKey;
        try {
            publicKey = issuerPublicKeyLoader.loadPublicKey(issuerDidTdw, extractKeyId(issuerSignedJWTToken));
        } catch (LoadingPublicKeyOfIssuerFailedException e) {
            throw credentialError(e, PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE, e.getMessage());
        }
        log.trace("Loaded issuer public key for id {}", managementEntity.getId());

        // Step 4 (SD-JWT spec 8.1 / 2.3): validate the Issuer (that it is in trust
        // registry)
        // -> validating against trust registry is not in scope of public beta (only
        // holder validates against trust)
        // -> so for now validation against base registry

        // Step 5 (SD-JWT spec 8.1 / 2.2): Validate the signature over the Issuer-signed
        // JWT
        Jws<Claims> claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(issuerSignedJWTToken);
        } catch (PrematureJwtException e) {
            throw credentialError(e, JWT_PREMATURE, "Could not verify JWT credential is not yet valid");
        } catch (ExpiredJwtException e) {
            throw credentialError(e, JWT_EXPIRED, "Could not verify JWT credential is expired");
        } catch (JwtException e) {
            throw credentialError(e, MALFORMED_CREDENTIAL, "Signature mismatch");
        }

        log.trace("Successfully verified signature of id {}", managementEntity.getId());
        // Step 6 (SD-JWT spec 8.1 / 2.4): Check that alg header is supported (currently
        // only alg="ES256" with type="vc+sd-jwt")
        var header = claims.getHeader();

        var requestedAlg = getRequestedFormat(CREDENTIAL_FORMAT, managementEntity).alg();
        if (!supportedAlgorithms.contains(header.getAlgorithm())
                || !Objects.equals(header.getType(), CREDENTIAL_FORMAT)) {
            throw credentialError(UNSUPPORTED_FORMAT, "Unsupported algorithm: " + header.getAlgorithm());
        }

        if (!requestedAlg.contains(header.getAlgorithm())) {
            throw credentialError(INVALID_FORMAT, "Invalid algorithm: %s requested %s".formatted(header.getAlgorithm(), requestedAlg));
        }

        // Step 7 (SD-JWT spec 8.3): Key Binding Verification
        Claims payload = claims.getPayload();
        int disclosureLength = parts.length;
        if (hasKeyBinding(payload)) {
            disclosureLength -= 1;
            log.trace("Verifying holder keybinding of id {}", managementEntity.getId());
            validateKeyBinding(payload, parts[parts.length - 1]);
            log.trace("Successfully verified holder keybinding of id {}", managementEntity.getId());
        }

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
        if (CollectionUtils.containsAny(disclosedClaimNames, claims.getPayload().keySet())) { // If there is any result of the set intersection
            throw credentialError(MALFORMED_CREDENTIAL, "Can not resolve disclosures. Existing Claim would be overridden.");
        }


        // check if distinct disclosures
        if (new HashSet<>(disclosures).size() != disclosures.size()) {
            throw credentialError(MALFORMED_CREDENTIAL,
                    "Request contains non-distinct disclosures");
        }

        // If any digest is missing or appears more than once in _sd, the check fails. This enforces that all disclosed digests are uniquely present in the _sd claim, as required by the SD-JWT specification.
        if (!digestsFromDisclosures.stream()
                .allMatch(dig -> Collections.frequency(payload.get("_sd", List.class), dig) == 1)) {
            throw credentialError(MALFORMED_CREDENTIAL,
                    "Could not verify JWT problem with disclosures and _sd field");
        }
        log.trace("Successfully verified disclosure digests of id {}", managementEntity.getId());

        // Confirm that the returned Credential(s) meet all criteria sent in the
        // Presentation Definition in the Authorization Request.
        var sdjwt = checkPresentationDefinitionCriteria(payload, disclosures);

        // The data submission is valid, we can now begin to check the status of the VC
        verifyStatus(payload);

        log.trace("Successfully verified the presented VC for id {}", managementEntity.getId());
        return sdjwt;
    }

    private void validateTrust(String issuerDidTdw) {

        var acceptedIssuerDids = managementEntity.getAcceptedIssuerDids();
        var acceptedIssuersEmpty = acceptedIssuerDids == null || acceptedIssuerDids.isEmpty();
        var trustAnchorDids = managementEntity.getTrustAnchors();
        var trustAnchorsEmpty = trustAnchorDids == null || trustAnchorDids.isEmpty();
        if(acceptedIssuersEmpty && trustAnchorsEmpty) {
            // -> if both, accepted issuers and trust anchors, are not set or empty, all issuers are allowed
            return;
        }


        if (!acceptedIssuersEmpty && acceptedIssuerDids.contains(issuerDidTdw)) {
            // Issuer trusted because it is in the accepted issuer dids
            return;
        }

        if (!trustAnchorsEmpty) {
            // Navigate Trust Statements to try to find the trust statements

        }



        throw credentialError(ISSUER_NOT_ACCEPTED, "Issuer not in list of accepted issuers or connected to trust anchor");
    }

    // Note: this method is package-private because this method is used in unit
    // tests, otherwise it could be private
    public String checkPresentationDefinitionCriteria(Claims claims, List<Disclosure> disclosures)
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

    private boolean hasKeyBinding(Claims payload) {
        boolean keyBindingProofPresent = !vpToken.endsWith("~");
        if (payload.containsKey("cnf") && !keyBindingProofPresent) {
            // There is a Holder Key Binding, but we did not receive a proof for it!
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }
        return keyBindingProofPresent;
    }

    private void validateKeyBinding(Claims payload, String keyBindingProof) {
        JWK keyBinding = getHolderKeyBinding(payload);
        // Validate Holder Binding Proof JWT

        JWTClaimsSet keyBindingClaims = getValidatedHolderKeyProof(keyBindingProof, keyBinding);
        validateNonce(keyBindingClaims);
        validateSDHash(keyBindingClaims);
    }

    private void validateSDHash(JWTClaimsSet keyBindingClaims) {
        // Compute the SD Hash of the VP Token
        String sdjwt = vpToken.substring(0, vpToken.lastIndexOf("~") + 1);
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
    private JWK getHolderKeyBinding(Claims payload) {
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

    private String extractIssuer(String jwtToken) {
        try {
            SignedJWT nimbusJwt = SignedJWT.parse(jwtToken);
            var issuer = nimbusJwt.getJWTClaimsSet().getIssuer();
            if (StringUtils.isBlank(issuer)) {
                throw credentialError(MALFORMED_CREDENTIAL, "Missing issuer in the JWT token");
            }
            return issuer;
        } catch (ParseException e) {
            throw credentialError(MALFORMED_CREDENTIAL, "Failed to extract issuer from JWT token");
        }
    }

    private String extractKeyId(String jwtToken) {
        try {
            var nimbusJwt = SignedJWT.parse(jwtToken);
            var keyId = nimbusJwt.getHeader().getKeyID();
            if (StringUtils.isBlank(keyId)) {
                throw credentialError(MALFORMED_CREDENTIAL, "Missing header attribute 'kid' for the issuer's Key Id in the JWT token");
            }
            return keyId;
        } catch (ParseException e) {
            throw credentialError(MALFORMED_CREDENTIAL, "Failed to extract key id from JWT token");
        }
    }
}