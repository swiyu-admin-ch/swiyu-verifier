package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.exception.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.model.statuslist.StatusListReferenceFactory;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.bit.eid.oid4vp.utils.Base64Utils;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDObjectDecoder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.PathNotFoundException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.PrematureJwtException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class SDJWTCredential extends CredentialVerifier {

    private final List<String> supportedAlgorithms = List.of("ES256");
    private final String credentialFormat = "vc+sd-jwt";
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;

    public SDJWTCredential(final String vpToken,
            final ManagementEntity managementEntity,
            final PresentationSubmission presentationSubmission,
            final VerificationManagementRepository verificationManagementRepository,
            final IssuerPublicKeyLoader issuerPublicKeyLoader,
            final StatusListReferenceFactory statusListReferenceFactory) {
        super(vpToken, managementEntity, presentationSubmission, verificationManagementRepository,
                statusListReferenceFactory);
        this.issuerPublicKeyLoader = issuerPublicKeyLoader;
    }

    /**
     * Verifies the presentation of a SD-JWT Credential as described in
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
     */
    @Override
    public void verifyPresentation() {

        // Step 1 (SD-JWT spec 8.1 / 1): Separate the SD-JWT into the Issuer-signed JWT
        // and the Disclosures (if any).
        String[] parts = vpToken.split("~");
        var issuerSignedJWTToken = parts[0];

        // Step 2 (SD-JWT spec 8.1 / 2.3): validate that the signing key belongs to the
        // Issuer ...
        PublicKey publicKey;
        try {
            publicKey = issuerPublicKeyLoader.loadPublicKey(issuerSignedJWTToken);
        } catch (LoadingPublicKeyOfIssuerFailedException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE,
                    e.getMessage(), managementEntity);
        }

        // Step 3 (SD-JWT spec 8.1 / 2.3): validate the Issuer (that it is in trust
        // registry)
        // -> validating against trust registry is not in scope of public beta (only
        // holder validates against trust)
        // -> so for now validation against base registry

        // Step 4 (SD-JWT spec 8.1 / 2.2): Validate the signature over the Issuer-signed
        // JWT
        Jws<Claims> claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(issuerSignedJWTToken);
        } catch (PrematureJwtException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                    "Could not verify JWT credential is not yet valid", managementEntity);
        } catch (ExpiredJwtException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                    "Could not verify JWT credential is expired", managementEntity);
        } catch (JwtException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                    "Signature mismatch", managementEntity);
        }

        // Step 5 (SD-JWT spec 8.1 / 2.4): Check that alg header is supported (currently
        // only alg="ES256" with type="vc+sd-jwt")
        var header = claims.getHeader();

        var requestedAlg = getRequestedFormat(credentialFormat).getAlg();
        if (!supportedAlgorithms.contains(header.getAlgorithm())
                || !Objects.equals(header.getType(), credentialFormat)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.UNSUPPORTED_FORMAT,
                    "Unsupported algorithm: " + header.getAlgorithm(), managementEntity);
        }

        if (!requestedAlg.contains(header.getAlgorithm())) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.INVALID_FORMAT,
                    "Invalid algorithm: %s requested %s".formatted(header.getAlgorithm(), requestedAlg),
                    managementEntity);
        }

        // Step 6 (SD-JWT spec 8.3): Key Binding Verification
        Claims payload = claims.getPayload();
        int disclosureLength = parts.length;
        if (hasKeyBinding(payload)) {
            disclosureLength -= 1;
            validateKeyBinding(payload, parts[parts.length - 1]);
        }

        // Step 7 (SD-JWT spec 8.1 / 3 ): Process the Disclosures and embedded digests
        // in the Issuer-signed JWT (section 3 in 8.1)
        List<Disclosure> disclosures = Arrays.stream(Arrays.copyOfRange(parts, 1, disclosureLength))
                .map(Disclosure::parse).toList();
        List<String> digestsFromDisclosures = disclosures.stream().map(Disclosure::digest).toList();

        // Note for SD-JWT spec 8.1 / 3.3.2: Check if "disclosures" contains an "_sd"
        // arguments and throw exception when thats the case
        // -> since we don't expect issuers to add "_sd" to the claim, we don't need to
        // check for this

        // check if distinct disclosures
        if (new HashSet<>(disclosures).size() != disclosures.size()) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                    "Request contains non-distinct disclosures", managementEntity);
        }

        if (!digestsFromDisclosures.stream()
                .allMatch(dig -> Collections.frequency(payload.get("_sd", List.class), dig) == 1)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                    "Could not verify JWT problem with disclosures and _sd field", managementEntity);
        }

        // Confirm that the returned Credential(s) meet all criteria sent in the
        // Presentation Definition in the Authorization Request.
        var sdjwt = checkPresentationDefinitionCriteria(payload, disclosures);

        // Check VC Status
        verifyStatus(payload);

        managementEntity.setState(VerificationStatusEnum.SUCCESS);
        managementEntity.setWalletResponse(ResponseData.builder().credentialSubjectData(sdjwt).build());
        verificationManagementRepository.save(managementEntity);
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
            sdJWTString = objectMapper.writeValueAsString(decodedSDJWT);
        } catch (PathNotFoundException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.CREDENTIAL_INVALID, e.getMessage(),
                    managementEntity);
        } catch (JsonProcessingException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                    "An error occurred while parsing SDJWT", managementEntity);
        }

        super.checkPresentationDefinitionCriteria(sdJWTString);

        return sdJWTString;
    }

    private boolean hasKeyBinding(Claims payload) {
        boolean keyBindingProofPresent = !vpToken.endsWith("~");
        if (payload.containsKey("cnf") && !keyBindingProofPresent) {
            // There is a Holder Key Binding, but we did not receive a proof for it!
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                    "Missing Holder Key Binding Proof", managementEntity);
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
            // If this occurs our static string is wrong...
            log.error("Failed to load hash algorithm", e);
            throw new RuntimeException(e);
        }
        String hashClaim = keyBindingClaims.getClaim("sd_hash").toString();
        if (!hash.equals(hashClaim)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                    String.format("Presented sd_hash '%s' does not match the computed hash '%s'", hashClaim, hash),
                    managementEntity);
        }
    }

    @NotNull
    private JWTClaimsSet getValidatedHolderKeyProof(String keyBindingProof, JWK keyBinding) {
        JWTClaimsSet keyBindingClaims;
        try {
            SignedJWT keyBindingJWT = SignedJWT.parse(keyBindingProof);

            if (!"kb+jwt".equals(keyBindingJWT.getHeader().getType().toString())) {
                throw VerificationException.credentialError(
                        ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                        String.format("Type of holder binding typ is expected to be kb+jwt but was %s",
                                keyBindingJWT.getHeader().getType().toString()),
                        managementEntity);
            }

            // Check if kb algorithm matches the required format
            var requestedKeyBindingAlg = getRequestedFormat(credentialFormat).getKeyBindingAlg();
            if (!requestedKeyBindingAlg.contains(keyBindingJWT.getHeader().getAlgorithm().getName())) {
                throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                        "Holder binding algorithm must be in %s".formatted(requestedKeyBindingAlg), managementEntity);
            }

            if (!keyBindingJWT.verify(new ECDSAVerifier(keyBinding.toECKey()))) {
                throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                        "Holder Binding provided does not match the one in the credential", managementEntity);
            }
            keyBindingClaims = keyBindingJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                    "Holder Binding could not be parsed", managementEntity);
        } catch (JOSEException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                    "Failed to verify the holder key binding - only supporting EC Keys", managementEntity);
        }
        return keyBindingClaims;
    }

    @NotNull
    private JWK getHolderKeyBinding(Claims payload) {
        if (!payload.containsKey("cnf")) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                    "No cnf claim found. Only supporting JWK holder bindings", managementEntity);
        }
        Object keyBindingClaim = payload.get("cnf");
        if (!(keyBindingClaim instanceof Map)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                    "Holder Binding is not a JWK", managementEntity);
        }
        JWK keyBinding;
        try {
            keyBinding = JWK.parse((Map<String, Object>) keyBindingClaim);
        } catch (ParseException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                    "Holder Binding Key could not be parsed", managementEntity);
        }
        return keyBinding;
    }

    private void validateNonce(JWTClaimsSet keyBindingClaims) {
        var expectedNonce = managementEntity.getRequestNonce();
        var actualNonce = keyBindingClaims.getClaim("nonce");
        if (!expectedNonce.equals(actualNonce)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.MISSING_NONCE,
                    String.format("Holder Binding lacks correct nonce expected '%s' but was '%s' ", expectedNonce,
                            actualNonce),
                    managementEntity);
        }
    }
}
