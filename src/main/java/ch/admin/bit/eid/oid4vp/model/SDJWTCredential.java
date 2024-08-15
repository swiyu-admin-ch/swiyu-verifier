package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.SDJWTConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import com.authlete.sd.Disclosure;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Slf4j
public class SDJWTCredential extends CredentialBuilder {

    private final SDJWTConfiguration sdjwtConfig;

    // TODO check what should be supported
    private final List<String> suggestedAlgorithms = List.of("ES256");

    SDJWTCredential(SDJWTConfiguration sdjwtConfig) {
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

        // TODO check format
        if (!suggestedAlgorithms.contains(header.getAlgorithm()) || !Objects.equals(header.getType(), "vc+sd-jwt")) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.UNSUPPORTED_FORMAT, "Unsupported algorithm: " + header.getAlgorithm());
        }

        Claims payload = claims.getPayload();

        // Confirm that the returned Credential(s) meet all criteria sent in the Presentation Definition in the Authorization Request.
        // TODO check if contains _sd maybe... checkPresentationDefinitionCriteria(document, jsonpathToCredential, managementEntity);

        var digests = payload.get("_sd", List.class);
        var disclosures = Arrays.copyOfRange(parts, 1, parts.length);
        var digestsFromDisclosures = Arrays.stream(disclosures).map(this::getDigestForDisclosure).toList();

        // check if distinct disclosures
        if (new HashSet<>(Arrays.asList(disclosures)).size() != disclosures.length) {
            updateManagementOnError(ResponseErrorCodeEnum.CREDENTIAL_INVALID);
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Request contains non-distinct disclosures");
        }

        if (!digestsFromDisclosures.stream().allMatch(dig -> Collections.frequency(digests, dig) == 1)) {
            updateManagementOnError(ResponseErrorCodeEnum.CREDENTIAL_INVALID);
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Could not verify JWT problem with disclosures and _sd field");
        }

        var walletResponseBuilder = ResponseData.builder();
        walletResponseBuilder.credentialSubjectData(vpToken);
        updateManagementObject(VerificationStatusEnum.SUCCESS, walletResponseBuilder.build());

        return managementEntity;
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

    private String getDigestForDisclosure(String disclosureString) {
        return Disclosure.parse(disclosureString).digest();
    }
}
