package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Adapter: nutzt den vorhandenen Legacy-SdjwtCredentialVerifier und liefert ein SdJwt
 * mit zusammengeführten Claims zurück, passend zum PresentationVerifier-Port.
 */
@Service
@RequiredArgsConstructor
public class SdjwtCredentialVerifierAdapter implements PresentationVerifier<String> {

    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;
    private final VerificationProperties verificationProperties;
    private final ApplicationProperties applicationProperties;

    @Override
    public String verify(String vpToken, Management management) {
        // Legacy-Verifier erwartet den VP-Token-String und liefert die entschlüsselten Claims als JSON-String.
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
