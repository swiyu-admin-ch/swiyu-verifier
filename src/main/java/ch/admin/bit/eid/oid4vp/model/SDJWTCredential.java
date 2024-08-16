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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.PrematureJwtException;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class SDJWTCredential extends CredentialBuilder {

    private final SDJWTConfiguration sdjwtConfig;

    // TODO check what should be supported
    private final List<String> suggestedAlgorithms = List.of("ES256");

    SDJWTCredential(SDJWTConfiguration sdjwtConfig) {
        this.sdjwtConfig = sdjwtConfig;
    }

    public SDJWTCredential(final String vpToken,
                           final ManagementEntity managementEntity,
                           final PresentationSubmission presentationSubmission,
                           final VerificationManagementRepository verificationManagementRepository,
                           SDJWTConfiguration sdjwtConfig) {
        credentialOffer(vpToken, managementEntity, presentationSubmission, verificationManagementRepository);
        this.sdjwtConfig = sdjwtConfig;
    }

    @Override
    // follows https://datatracker.ietf.org/doc/html/draft-ietf-oauth-selective-disclosure-jwt#section-8.1-4.3.2.4
    public ManagementEntity verifyPresentation() {

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
            log.error("JWT verification failed", e);
            updateManagementOnError(ResponseErrorCodeEnum.CREDENTIAL_INVALID);
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Could not verify JWT credential is not yet valid");
        } catch (ExpiredJwtException e) {
            log.error("JWT verification failed", e);
            updateManagementOnError(ResponseErrorCodeEnum.CREDENTIAL_INVALID);
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Could not verify JWT credential is expired");
        } catch (JwtException e) {
            log.error("JWT verification failed", e);
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Signature mismatch");
        }

        // Checks if the presentation is expired and if it can already be used
        var header = claims.getHeader();

        if (!suggestedAlgorithms.contains(header.getAlgorithm()) || !Objects.equals(header.getType(), "vc+sd-jwt")) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.UNSUPPORTED_FORMAT, "Unsupported algorithm: " + header.getAlgorithm());
        }

        Claims payload = claims.getPayload();

        List<Disclosure> disclosures = Arrays.stream(Arrays.copyOfRange(parts, 1, parts.length)).map(Disclosure::parse).toList();
        List<String> digestsFromDisclosures = disclosures.stream().map(Disclosure::digest).toList();

        // check if distinct disclosures
        if (new HashSet<>(disclosures).size() != disclosures.size()) {
            updateManagementOnError(ResponseErrorCodeEnum.CREDENTIAL_INVALID);
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Request contains non-distinct disclosures");
        }

        if (!digestsFromDisclosures.stream().allMatch(dig -> Collections.frequency(payload.get("_sd", List.class), dig) == 1)) {
            updateManagementOnError(ResponseErrorCodeEnum.CREDENTIAL_INVALID);
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Could not verify JWT problem with disclosures and _sd field");
        }

        // Confirm that the returned Credential(s) meet all criteria sent in the Presentation Definition in the Authorization Request.
        checkPresentationDefinitionCriteria(payload, disclosures);

        var walletResponseBuilder = ResponseData.builder();
        walletResponseBuilder.credentialSubjectData(vpToken);
        updateManagementObject(VerificationStatusEnum.SUCCESS, walletResponseBuilder.build());

        return managementEntity;
    }

    public boolean checkPresentationDefinitionCriteria(Claims claims, List<Disclosure> disclosures) throws VerificationException {
        Map<String, Object> expectedMap = new HashMap<>(claims);
        SDObjectDecoder decoder = new SDObjectDecoder();
        ObjectMapper objectMapper = new ObjectMapper();
        String sdJWTString;

        try {
            Map<String, Object> decodedSDJWT = decoder.decode(expectedMap, disclosures);
            sdJWTString = objectMapper.writeValueAsString(decodedSDJWT);
        } catch (PathNotFoundException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, e.getMessage());
        } catch (JsonProcessingException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "An error occurred while parsing SDJWT");
        }

        super.checkPresentationDefinitionCriteria(sdJWTString);

        return true;
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
            return null;
        }
    }
}
