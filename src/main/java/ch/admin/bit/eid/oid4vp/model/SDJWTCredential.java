package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.SDJWTConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
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

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class SDJWTCredential extends CredentialVerifier {

    private final SDJWTConfiguration sdjwtConfig;

    // TODO check what should be supported
    private final List<String> suggestedAlgorithms = List.of("ES256");

    public SDJWTCredential(final String vpToken,
                           final ManagementEntity managementEntity,
                           final PresentationSubmission presentationSubmission,
                           final VerificationManagementRepository verificationManagementRepository,
                           SDJWTConfiguration sdjwtConfig) {

        super(vpToken, managementEntity, presentationSubmission, verificationManagementRepository);
        this.sdjwtConfig = sdjwtConfig;
    }

    @Override
    // follows https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#section-8.1-4.3.2.4
    public void verifyPresentation() {

        Jws<Claims> claims;
        String[] parts = vpToken.split("~");
        var issuerSignedJWTToken = parts[0];
        var publicKey = loadPublicKey();

        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(issuerSignedJWTToken);
        } catch (PrematureJwtException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Could not verify JWT credential is not yet valid", managementEntity);
        } catch (ExpiredJwtException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Could not verify JWT credential is expired", managementEntity);
        } catch (JwtException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Signature mismatch", managementEntity);
        }

        // Checks if the presentation is expired and if it can already be used
        var header = claims.getHeader();

        if (!suggestedAlgorithms.contains(header.getAlgorithm()) || !Objects.equals(header.getType(), "vc+sd-jwt")) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.UNSUPPORTED_FORMAT, "Unsupported algorithm: " + header.getAlgorithm(), managementEntity);
        }

        Claims payload = claims.getPayload();
        int disclosureLength = parts.length;
        if (hasKeyBinding(payload)) {
            disclosureLength-=1;
            validateKeyBinding(payload, parts[parts.length-1]);
        }
        List<Disclosure> disclosures = Arrays.stream(Arrays.copyOfRange(parts, 1, disclosureLength)).map(Disclosure::parse).toList();
        List<String> digestsFromDisclosures = disclosures.stream().map(Disclosure::digest).toList();


        // check if distinct disclosures
        if (new HashSet<>(disclosures).size() != disclosures.size()) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Request contains non-distinct disclosures", managementEntity);
        }

        if (!digestsFromDisclosures.stream().allMatch(dig -> Collections.frequency(payload.get("_sd", List.class), dig) == 1)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Could not verify JWT problem with disclosures and _sd field", managementEntity);
        }

        // Confirm that the returned Credential(s) meet all criteria sent in the Presentation Definition in the Authorization Request.
        var sdjwt = checkPresentationDefinitionCriteria(payload, disclosures);

        managementEntity.setState(VerificationStatusEnum.SUCCESS);
        managementEntity.setWalletResponse(ResponseData.builder().credentialSubjectData(sdjwt).build());
        verificationManagementRepository.save(managementEntity);
    }

    private boolean hasKeyBinding(Claims payload) {
        boolean keyBindingProofPresent = !vpToken.endsWith("~");
        if (payload.containsKey("cnf") && !keyBindingProofPresent) {
            // There is a Holder Key Binding, but we did not receive a proof for it!
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof", managementEntity);
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
        String sdjwt = vpToken.substring(0, vpToken.lastIndexOf("~")+1);
        String hash = null;
        try {
            hash = new String(Base64.getUrlEncoder().withoutPadding().encode(MessageDigest.getInstance("sha-256").digest(sdjwt.getBytes())));
        } catch (NoSuchAlgorithmException e) {
            // If this occurs our static string is wrong...
            log.error("Failed to load hash algorithm", e);
            throw new RuntimeException(e);
        }
        String hashClaim = keyBindingClaims.getClaim("sd_hash").toString();
        if (!hash.equals(hashClaim)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, String.format("Presented sd_hash '%s' does not match the computed hash '%s'", hashClaim, hash), managementEntity);
        }
    }

    @NotNull
    private JWTClaimsSet getValidatedHolderKeyProof(String keyBindingProof, JWK keyBinding) {
        JWTClaimsSet keyBindingClaims = null;
        try {
            SignedJWT keyBindingJWT = SignedJWT.parse(keyBindingProof);
            if (!"kb+jwt".equals(keyBindingJWT.getHeader().getType().toString())) {
                throw VerificationException.credentialError(
                        ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, String.format("Type of holder binding typ is expected to be kb+jwt but was %s", keyBindingJWT.getHeader().getType().toString()), managementEntity);
            }
            if (!keyBindingJWT.verify(new ECDSAVerifier(keyBinding.toECKey()))) {
                throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, "Holder Binding provided does not match the one in the credential", managementEntity);
            }
            keyBindingClaims = keyBindingJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, "Holder Binding could not be parsed", managementEntity);
        } catch (JOSEException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, "Failed to verify the holder key binding - only supporting EC Keys", managementEntity);
        }
        return keyBindingClaims;
    }

    @NotNull
    private JWK getHolderKeyBinding(Claims payload) {
        if (!payload.containsKey("cnf")) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, "No cnf claim found. Only supporting JWK holder bindings", managementEntity);
        }
        Object keyBindingClaim = payload.get("cnf");
        if (!(keyBindingClaim instanceof Map)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, "Holder Binding is not a JWK", managementEntity);
        }
        JWK keyBinding = null;
        try {
            keyBinding = JWK.parse((Map<String, Object>) keyBindingClaim);
        } catch (ParseException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, "Holder Binding Key could not be parsed", managementEntity);
        }
        return keyBinding;
    }

    private void validateNonce(JWTClaimsSet keyBindingClaims) {
        var expectedNonce = managementEntity.getRequestNonce();
        var actualNonce = keyBindingClaims.getClaim("nonce");
        if (!expectedNonce.equals(actualNonce)) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.MISSING_NONCE, String.format("Holder Binding lacks correct nonce expected '%s' but was '%s' ", expectedNonce, actualNonce ), managementEntity);
        }
    }


    public String checkPresentationDefinitionCriteria(Claims claims, List<Disclosure> disclosures) throws VerificationException {
        Map<String, Object> expectedMap = new HashMap<>(claims);
        SDObjectDecoder decoder = new SDObjectDecoder();
        ObjectMapper objectMapper = new ObjectMapper();
        String sdJWTString;

        try {
            Map<String, Object> decodedSDJWT = decoder.decode(expectedMap, disclosures);
            sdJWTString = objectMapper.writeValueAsString(decodedSDJWT);
        } catch (PathNotFoundException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, e.getMessage(), managementEntity);
        } catch (JsonProcessingException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "An error occurred while parsing SDJWT", managementEntity);
        }

        super.checkPresentationDefinitionCriteria(sdJWTString);

        return sdJWTString;
    }

    // TODO replace with actual functionality
    private PublicKey loadPublicKey() {
        try {
            var sanitized = sdjwtConfig.getPublicKey().replace("\n", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
            byte[] encoded = Base64.getDecoder().decode(sanitized);
            KeyFactory kf = KeyFactory.getInstance("EC");
            EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            return kf.generatePublic(keySpec);
        } catch (Exception e) {
            log.error("Failed to load public key", e);
            throw new IllegalArgumentException("Public key could not be loaded - Please check the env variables and try again");
        }
    }
}
