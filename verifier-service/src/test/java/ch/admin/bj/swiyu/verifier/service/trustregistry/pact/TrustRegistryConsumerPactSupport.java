package ch.admin.bj.swiyu.verifier.service.trustregistry.pact;

import au.com.dius.pact.consumer.MockServer;
import ch.admin.bj.swiyu.core.trust.client.api.TrustProtocol20Api;
import ch.admin.bj.swiyu.core.trust.client.invoker.ApiClient;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.service.trustregistry.CacheMaintenanceService;
import ch.admin.bj.swiyu.verifier.service.trustregistry.TrustStatementCacheService;

import java.util.Optional;

import static org.mockito.Mockito.mock;

final class TrustRegistryConsumerPactSupport {

    static final String CONSUMER = "swiyu-verifier";
    static final String PROVIDER = "swiyu-trust-registry";

    static final String ISSUER_DID =
            "did:tdw:QmYyQSo1c1Ym7orWxLYvCrzRLZad5ZxQ8HkBLyEE4RRBB1:identifier.admin.ch:api:v1:did";
    static final String VERIFIER_DID =
            "did:tdw:QmWmyoMoctfbAaiEs5r9gf3vQfvT9mZQh1kStKa8BRcMT5:identifier.admin.ch:api:v1:did";
    static final String NON_COMPLIANT_ACTOR_DID = "did:example:non-compliant-actor";

    static final String VCT_ELFA = "https://example.ch/vct/elfa";
    static final String VCT_MDL = "https://example.ch/vct/mdl";
    static final String PROTECTED_FIELD_FIRST_NAME = "first_name";
    static final String PROTECTED_FIELD_PERSONAL_ADMINISTRATIVE_NUMBER = "personal_administrative_number";

    static final String COMPACT_JWT_REGEX =
            "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$";

    private TrustRegistryConsumerPactSupport() {
    }

    static TrustProtocol20Api buildApi(final MockServer mockServer) {
        final ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(mockServer.getUrl());
        return new TrustProtocol20Api(apiClient);
    }

    static TrustStatementCacheService buildCacheService(final MockServer mockServer) {
        final TrustRegistryProperties properties = new TrustRegistryProperties();
        properties.setMaxCacheSize(100);
        properties.setClockSkewBufferSeconds(0);
        properties.setMaxCacheTtlSeconds(0);

        return new TrustStatementCacheService(
                buildApi(mockServer),
                properties,
                mock(CacheMaintenanceService.class),
                Optional.empty());
    }
}
