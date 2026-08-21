package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.jweutil.JweDecryptionLimits;
import ch.admin.bj.swiyu.jweutil.JweUtil;
import ch.admin.bj.swiyu.jweutil.JweUtilException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationUnionDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Optional;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationError.INVALID_REQUEST;

/**
 * Technical service responsible solely for JWE decryption of verification responses.
 * <p>
 * It does not contain any business logic related to VP API versions or payload mapping.
 */
@Service
@RequiredArgsConstructor
public class JweDecryptionService {

    private final ObjectMapper objectMapper;
    private final ApplicationProperties applicationProperties;

    public VerificationPresentationUnionDto decrypt(Management managementEntity,
                                                    VerificationPresentationUnionDto verificationResponse) {
        try {
            String jweString = verificationResponse.getResponse();
            String keyId = Optional.ofNullable(JWEObject.parse(jweString).getHeader().getKeyID())
                    .orElseThrow(() -> VerificationException.submissionError(
                            INVALID_REQUEST,
                            "Missing keyId. Unable to decrypt response."));
            JWK privateKey = resolvePrivateKey(managementEntity, keyId);
            String payload = JweUtil.decrypt(jweString, privateKey, resolveDecryptionLimits());
            return objectMapper.readValue(payload, VerificationPresentationUnionDto.class);
        } catch (ParseException e) {
            throw VerificationException.credentialError(e, "Failed to parse response.");
        } catch (JweUtilException e) {
            throw VerificationException.credentialError(e, "Response cannot be decrypted.");
        } catch (JacksonException e) {
            throw VerificationException.credentialError(e, e.getOriginalMessage());
        }
    }

    /**
     * Builds the JWE size limits enforced by {@code swiyu-jwe-util} from the application configuration.
     */
    private JweDecryptionLimits resolveDecryptionLimits() {
        return new JweDecryptionLimits(
                applicationProperties.getMaxCompressedCipherTextLength(),
                applicationProperties.getMaxDecompressedPayloadLength());
    }

    @NotNull
    private static JWK resolvePrivateKey(Management managementEntity, String keyId)
            throws ParseException {
        ResponseSpecification responseSpecification = managementEntity.getResponseSpecification();
        JWKSet privateKeys = JWKSet.parse(Optional.ofNullable(responseSpecification.getJwksPrivate())
                // Throw illegal state, as this would be a server error
                .orElseThrow(() -> new IllegalStateException("Missing JWK private. Unable to decrypt response.")));
        return Optional.ofNullable(privateKeys.getKeyByKeyId(keyId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No matching JWK for keyId %s found. Unable to decrypt response.".formatted(keyId)));
    }
}
