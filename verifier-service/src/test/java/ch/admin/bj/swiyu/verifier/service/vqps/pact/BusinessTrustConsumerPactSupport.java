package ch.admin.bj.swiyu.verifier.service.vqps.pact;

import au.com.dius.pact.consumer.MockServer;
import ch.admin.bj.swiyu.core.trust.client.api.VqpsSubmissionB2BApi;
import ch.admin.bj.swiyu.core.trust.client.invoker.ApiClient;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.vqps.Vqps;
import ch.admin.bj.swiyu.verifier.domain.vqps.VqpsRepository;
import ch.admin.bj.swiyu.verifier.service.vqps.VqpsRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class BusinessTrustConsumerPactSupport {

    static final String CONSUMER = "swiyu-verifier";
    static final String PROVIDER = "swiyu-core-business-service";
    static final String VERIFIER_DID =
            "did:webvh:QmWmyoMoctfbAaiEs5r9gf3vQfvT9mZQh1kStKa8BRcMT5:identifier.admin.ch:api:v1:did";
    static final String TEST_ACCESS_TOKEN = "pact-test-access-token";
    static final String COMPACT_JWT_REGEX =
            "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$";

    private BusinessTrustConsumerPactSupport() {
    }

    static VqpsPactFixture buildFixture(final MockServer mockServer) {
        final ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(mockServer.getUrl());
        apiClient.setBearerToken(TEST_ACCESS_TOKEN);

        final TrustRegistryProperties trustRegistryProperties = new TrustRegistryProperties();
        trustRegistryProperties.setVqpsExpiryBufferSeconds(0);

        final ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.setClientId(VERIFIER_DID);

        final VqpsRepository repository = mock(VqpsRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Vqps.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final VqpsRegistrationService service = new VqpsRegistrationService(
                trustRegistryProperties,
                applicationProperties,
                repository,
                new VqpsSubmissionB2BApi(apiClient),
                new ObjectMapper());
        return new VqpsPactFixture(service, repository);
    }

    record VqpsPactFixture(VqpsRegistrationService service, VqpsRepository repository) {
    }
}
