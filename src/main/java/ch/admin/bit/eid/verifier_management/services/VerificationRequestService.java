package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.config.ApplicationConfig;
import ch.admin.bit.eid.verifier_management.config.OpenId4VPConfig;
import ch.admin.bit.eid.verifier_management.models.entities.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.entities.VerificationRequestObject;
import ch.admin.bit.eid.verifier_management.repositories.VerificationRequestObjectRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static ch.admin.bit.eid.verifier_management.utils.TimeUtils.getTTL;

@Service
@AllArgsConstructor
public class VerificationRequestService {

    private static final String OAUTH_RESPONSE_MODE = "direct_post";

    private final ApplicationConfig appConfig;

    private final OpenId4VPConfig openId4VPConfig;

    private final VerificationRequestObjectRepository repository;

    public VerificationRequestObject createAuthorizationRequest(final PresentationDefinition presentationDefinition) {

        if (presentationDefinition == null) {
            throw new IllegalArgumentException("PresentationDefinition is null");
        }

        UUID uuid = UUID.randomUUID();
        String uri = String.format(openId4VPConfig.getRequestObjectResponsePattern(), uuid);

        VerificationRequestObject verificationRequest = VerificationRequestObject.builder()
                .id(uuid)
                .nonce(createNonce())
                .responseMode(OAUTH_RESPONSE_MODE)
                .responseUri(uri)
                .clientMetadata(presentationDefinition.getClientMetadata())
                .presentationDefinition(presentationDefinition)
                .expiresAt(getTTL(appConfig.getVerificationTTL()))
                .build();

        return repository.save(verificationRequest);
    }

    // TODO check -> was nonce=uuid.uuid4().hex
    private String createNonce() {
        final SecureRandom random = new SecureRandom();
        final Base64.Encoder base64encoder = Base64.getEncoder().withoutPadding();

        byte[] randomBytes = new byte[24];
        random.nextBytes(randomBytes);

        return base64encoder.encodeToString(randomBytes);
    }
}
