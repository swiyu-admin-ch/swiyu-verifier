package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.LegacyPresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Adapter implementation of {@link LegacyPresentationVerifier} for SD-JWT based verifiable presentations.
 * <p>
 * This class bridges the legacy {@link SdjwtCredentialVerifier} implementation to the
 * {@link LegacyPresentationVerifier} port used by the OID4VP layer. It wires all required collaborators
 * and delegates the actual verification logic to the legacy verifier.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Accept a VP token and corresponding {@link Management} configuration.</li>
 *   <li>Instantiate and configure {@link SdjwtCredentialVerifier} with the required dependencies.</li>
 *   <li>Return the verification result produced by the legacy verifier (a JSON string with merged claims).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SdjwtCredentialVerifierAdapterLegacy implements LegacyPresentationVerifier {

    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;
    private final VerificationProperties verificationProperties;
    private final ApplicationProperties applicationProperties;

    /**
     * Verifies the given SD-JWT based verifiable presentation.
     * <p>
     * This method constructs a {@link SdjwtCredentialVerifier} with all required collaborators
     * and delegates the verification to {@link SdjwtCredentialVerifier#verifyPresentation()}.
     *
     * @param vpToken   the raw VP token (typically an SD-JWT representation) to verify
     * @param management the management configuration that defines how the credential must be validated
     * @return a JSON string containing the merged claims of the verified presentation
     */
    @Override
    public String verify(String vpToken, Management management) {
        var legacy = new SdjwtCredentialVerifier(
                vpToken,
                management,
                issuerPublicKeyLoader,
                statusListReferenceFactory,
                objectMapper,
                verificationProperties,
                applicationProperties
        );
        return legacy.verifyPresentation();
    }
}
