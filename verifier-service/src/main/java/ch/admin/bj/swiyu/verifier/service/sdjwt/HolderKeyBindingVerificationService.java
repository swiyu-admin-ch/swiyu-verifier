package ch.admin.bj.swiyu.verifier.service.sdjwt;

import ch.admin.bj.swiyu.sdjwtverifier.SdJwt;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtVcValidator;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtVerificationException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

/**
 * Verifies a SD-JWT Holder Key Binding indicating that the SD-JWT part of a VC was sent by the owner of the private key
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HolderKeyBindingVerificationService {

    private final ApplicationProperties applicationProperties;
    private final VerificationProperties verificationProperties;

    /**
     * Validate the holder (key) binding for an SD-JWT that represents a Verifiable Presentation (VP) token.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>If cryptographic holder-binding is not required and the token contains no key binding, return silently.</li>
     *   <li>If cryptographic holder-binding is required but the token lacks a key binding, throw a
     *       credential error with code HOLDER_BINDING_MISMATCH.</li>
     *   <li>Otherwise, validate the holder-binding</li>
     * </ul>
     *
     * @param sdJwt                                the parsed SD-JWT {@link SdJwt} containing the VP token to validate
     * @param isCryptographicHolderBindingRequired boolean whether cryptographic holder-binding is mandatory
     * @param management                           the Management {@link Management} provides configuration override and request nonce
     * @param validator                            the SD-JWT VC validator used to perform the low-level key-binding checks
     */
    public void validateKeyBinding(SdJwt sdJwt, boolean isCryptographicHolderBindingRequired, Management management, SdJwtVcValidator validator) {

        if (!isCryptographicHolderBindingRequired && !sdJwt.hasKeyBinding()) {
            return;
        }

        if (isCryptographicHolderBindingRequired && !sdJwt.hasKeyBinding()) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }

        var configurationOverride = Optional.ofNullable(management.getConfigurationOverride())
                .orElse(new ConfigurationOverride(null, null, null, null, null, null));

        var expectedAudience = configurationOverride.verifierDidOrDefaultWithPrefix(applicationProperties);
        var requestNonce = management.getRequestNonce();

        try {
            validator.validateKeyBinding(sdJwt,
                    expectedAudience,
                    requestNonce,
                    verificationProperties.getAcceptableProofTimeWindowSeconds());

        } catch (SdJwtVerificationException e) {
            log.error("Failed to validate key binding for VP token: {}", e.getMessage(), e);
            throw credentialError(e, HOLDER_BINDING_MISMATCH, e.getMessage());
        }
    }
}
