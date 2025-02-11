/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain;

import ch.admin.bj.swiyu.verifier.oid4vp.common.base64.Base64Utils;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntity;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist.StatusListReference;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist.StatusListReferenceFactory;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDObjectDecoder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.PathNotFoundException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static ch.admin.bj.swiyu.verifier.oid4vp.domain.CredentialVerifierSupport.checkCommonPresentationDefinitionCriteria;
import static ch.admin.bj.swiyu.verifier.oid4vp.domain.CredentialVerifierSupport.getRequestedFormat;
import static ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationErrorResponseCode.*;
import static ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException.credentialError;
import static org.springframework.util.StringUtils.hasText;

/**
 * Verifies the presentation of a SD-JWT Credential.
 */
@AllArgsConstructor
@Slf4j
public class SdjwtCredentialVerifier {

    public static final String CREDENTIAL_FORMAT = "vc+sd-jwt";

    private final List<String> supportedAlgorithms = List.of("ES256");
    private final String vpToken;
    private final ManagementEntity managementEntity;
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;


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

        // Step 2: Check if issuer is in list of accepted issuers
        // -> if accepted issuers are not set or empty, all issuers are allowed
        var issuerDidTdw = extractIssuer(issuerSignedJWTToken);
        if (managementEntity.getAcceptedIssuerDids() != null
                && !managementEntity.getAcceptedIssuerDids().isEmpty()
                && !managementEntity.getAcceptedIssuerDids().contains(issuerDidTdw)) {
            throw credentialError(ISSUER_NOT_ACCEPTED, "Issuer not in list of accepted issuers");
        }

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
            throw credentialError(e, CREDENTIAL_INVALID, "Could not verify JWT credential is not yet valid");
        } catch (ExpiredJwtException e) {
            throw credentialError(e, CREDENTIAL_INVALID, "Could not verify JWT credential is expired");
        } catch (JwtException e) {
            throw credentialError(e, CREDENTIAL_INVALID, "Signature mismatch");
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
        List<Disclosure> disclosures = Arrays.stream(Arrays.copyOfRange(parts, 1, disclosureLength))
                .map(Disclosure::parse).toList();
        List<String> digestsFromDisclosures = disclosures.stream().map(Disclosure::digest).toList();
        log.trace("Prepared {} disclosure digests for id {}", disclosures.size(), managementEntity.getId());

        var disclosedClaimNames = disclosures.stream().map(Disclosure::getClaimName).collect(Collectors.toSet());

        // 8.1 / 3.2.2 If the claim name is _sd or ..., the SD-JWT MUST be rejected.
        if (CollectionUtils.containsAny(disclosedClaimNames, Set.of("_sd", "..."))) {
            throw credentialError(CREDENTIAL_INVALID, "Illegal disclosure found with name _sd or ...");
        }

        // 8.1 / 3.3.3: If the claim name already exists at the level of the _sd key, the SD-JWT MUST be rejected.
        if (CollectionUtils.containsAny(disclosedClaimNames, claims.getPayload().keySet())) { // If there is any result of the set intersection
            throw credentialError(CREDENTIAL_INVALID, "Can not resolve disclosures. Existing Claim would be overridden.");
        }


        // check if distinct disclosures
        if (new HashSet<>(disclosures).size() != disclosures.size()) {
            throw credentialError(CREDENTIAL_INVALID,
                    "Request contains non-distinct disclosures");
        }
        if (!digestsFromDisclosures.stream()
                .allMatch(dig -> Collections.frequency(payload.get("_sd", List.class), dig) == 1)) {
            throw credentialError(CREDENTIAL_INVALID,
                    "Could not verify JWT problem with disclosures and _sd field");
        }
        log.trace("Successfully verified disclosure digests of id {}", managementEntity.getId());

        // Check VC Status
        verifyStatus(payload);
        // The VC is valid, we can now begin to check the data submission

        // Confirm that the returned Credential(s) meet all criteria sent in the
        // Presentation Definition in the Authorization Request.
        var sdjwt = checkPresentationDefinitionCriteria(payload, disclosures);
        log.trace("Successfully verified the presented VC for id {}", managementEntity.getId());
        return sdjwt;
    }

    // Note: this method is package-private because this method is used in unit
    // tests, otherwise it could be private
    String checkPresentationDefinitionCriteria(Claims claims, List<Disclosure> disclosures)
            throws VerificationException {
        Map<String, Object> expectedMap = new HashMap<>(claims);
        SDObjectDecoder decoder = new SDObjectDecoder();
        ObjectMapper objectMapper = new ObjectMapper();
        String sdJWTString;

        try {
            Map<String, Object> decodedSDJWT = decoder.decode(expectedMap, disclosures);
            log.trace("Decoded SD-JWT to clear data for id {}", managementEntity.getId());
            sdJWTString = objectMapper.writeValueAsString(decodedSDJWT);
        } catch (PathNotFoundException e) {
            throw credentialError(e, CREDENTIAL_INVALID, e.getMessage());
        } catch (JsonProcessingException e) {
            throw credentialError(e, CREDENTIAL_INVALID, "An error occurred while parsing SDJWT");
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

            if (!"kb+jwt".equals(keyBindingJWT.getHeader().getType().toString())) {
                throw credentialError(HOLDER_BINDING_MISMATCH,
                        String.format("Type of holder binding typ is expected to be kb+jwt but was %s",
                                keyBindingJWT.getHeader().getType().toString()));
            }

            // Check if kb algorithm matches the required format
            var requestedKeyBindingAlg = getRequestedFormat(CREDENTIAL_FORMAT, managementEntity).keyBindingAlg();
            if (!requestedKeyBindingAlg.contains(keyBindingJWT.getHeader().getAlgorithm().getName())) {
                throw credentialError(HOLDER_BINDING_MISMATCH, "Holder binding algorithm must be in %s".formatted(requestedKeyBindingAlg));
            }

            if (!keyBindingJWT.verify(new ECDSAVerifier(keyBinding.toECKey()))) {
                throw credentialError(HOLDER_BINDING_MISMATCH, "Holder Binding provided does not match the one in the credential");
            }
            keyBindingClaims = keyBindingJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw credentialError(e, HOLDER_BINDING_MISMATCH, "Holder Binding could not be parsed");
        } catch (JOSEException e) {
            throw credentialError(e, HOLDER_BINDING_MISMATCH, "Failed to verify the holder key binding - only supporting EC Keys");
        }
        return keyBindingClaims;
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
        JWK keyBinding;
        try {
            keyBinding = JWK.parse((Map<String, Object>) keyBindingClaim);
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
                    String.format("Holder Binding lacks correct nonce expected '%s' but was '%s' ", expectedNonce,
                            actualNonce));
        }
    }

    private String extractIssuer(String jwtToken) {
        var decodedBody = Base64.getDecoder().decode(jwtToken.split("\\.")[1]);
        var body = toJson(decodedBody);
        var issuer = body.get("iss");
        if (issuer == null || !hasText(issuer.asText())) {
            throw new IllegalArgumentException("Missing issuer in the JWT token");
        }
        return issuer.asText();
    }

    private String extractKeyId(String jwtToken) {
        try {
            var nimbusJwt = SignedJWT.parse(jwtToken);
            var keyId = nimbusJwt.getHeader().getKeyID();
            if (StringUtils.isBlank(keyId)) {
                throw new IllegalArgumentException("Missing header attribute 'kid' for the issuer's Key Id in the JWT token");
            }
            return keyId;
        } catch (ParseException e) {
            throw new IllegalArgumentException("failed to parse json", e);
        }
    }

    private JsonNode toJson(byte[] json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to parse json", e);
        }
    }
}
