package ch.admin.bj.swiyu.verifier.service.publickey.pact.if003;

import au.com.dius.pact.consumer.MockServer;
import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;
import ch.admin.bj.swiyu.didresolveradapter.DidResolverWebClient;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

final class BaseRegistryConsumerPactSupport {

    static final String CONSUMER = "swiyu-verifier";
    static final String PROVIDER = "swiyu-identifier-registry";

    static final String REGISTRY_ORIGIN = "https://identifier-reg.trust-infra.swiyu-int.admin.ch";
    static final String DID_ID = "18fa7c77-9dd1-4e20-a147-fb1bec146085";
    static final String SCID = "QmT4kPBFsHpJKvvvxgFUYxnSGPMeaQy1HWwyXMHj8NjLuy";
    static final String DID = "did:webvh:" + SCID
            + ":identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:" + DID_ID;
    static final String VERIFICATION_METHOD_FRAGMENT = "assert-key-01";
    static final String VERIFICATION_METHOD_ID = DID + "#" + VERIFICATION_METHOD_FRAGMENT;
    static final String DID_LOG_PATH = "/api/v1/did/" + DID_ID + "/did.jsonl";

    static final String DID_LOG = """
            {"versionId":"1-QmcKguBa4nUiUqEmc8iqLUGAm8iWQsa95PgkTVg1krBHGY","versionTime":"2025-08-19T10:22:47Z","parameters":{"method":"did:webvh:1.0","scid":"QmT4kPBFsHpJKvvvxgFUYxnSGPMeaQy1HWwyXMHj8NjLuy","updateKeys":["z6MkpGZULZQdxiEuHvb26979gp1CzfqA3MJz61sXBFhoBkpj"],"portable":false},"state":{"@context":["https://www.w3.org/ns/did/v1","https://w3id.org/security/jwk/v1"],"id":"did:webvh:QmT4kPBFsHpJKvvvxgFUYxnSGPMeaQy1HWwyXMHj8NjLuy:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:18fa7c77-9dd1-4e20-a147-fb1bec146085","authentication":["did:webvh:QmT4kPBFsHpJKvvvxgFUYxnSGPMeaQy1HWwyXMHj8NjLuy:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:18fa7c77-9dd1-4e20-a147-fb1bec146085#auth-key-01"],"assertionMethod":["did:webvh:QmT4kPBFsHpJKvvvxgFUYxnSGPMeaQy1HWwyXMHj8NjLuy:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:18fa7c77-9dd1-4e20-a147-fb1bec146085#assert-key-01"],"verificationMethod":[{"id":"did:webvh:QmT4kPBFsHpJKvvvxgFUYxnSGPMeaQy1HWwyXMHj8NjLuy:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:18fa7c77-9dd1-4e20-a147-fb1bec146085#auth-key-01","type":"JsonWebKey2020","publicKeyJwk":{"kty":"EC","crv":"P-256","x":"4YF5Uwoer2cQw-jMZI9VjzF3v9eoLRDo45gb7cCaOMc","y":"bRmtFg_NiaGZtLl-9snXKW6ZDifSiRTeTgLiguJXCOc","kid":"auth-key-01"}},{"id":"did:webvh:QmT4kPBFsHpJKvvvxgFUYxnSGPMeaQy1HWwyXMHj8NjLuy:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:18fa7c77-9dd1-4e20-a147-fb1bec146085#assert-key-01","type":"JsonWebKey2020","publicKeyJwk":{"kty":"EC","crv":"P-256","x":"OqySiIDlP_OzFkOO3wvnWtjVYsPdc_u1q8vr1DHlouA","y":"i0o3elG8YlP6AkPqHicIvMgmQfZ99ZWRLSYfcpTPi1A","kid":"assert-key-01"}}]},"proof":[{"type":"DataIntegrityProof","cryptosuite":"eddsa-jcs-2022","created":"2025-08-19T10:22:47Z","verificationMethod":"did:key:z6MkpGZULZQdxiEuHvb26979gp1CzfqA3MJz61sXBFhoBkpj#z6MkpGZULZQdxiEuHvb26979gp1CzfqA3MJz61sXBFhoBkpj","proofPurpose":"assertionMethod","proofValue":"zau6HS96woyYY5o157TA6QoSaN3CTQXVNtbN69H11ApnFa7MsVak1SmupHAmepfzfTpJ6sTPV8GZZY7MCcgiBsnD"}]}
            """.strip();

    private BaseRegistryConsumerPactSupport() {
    }

    static DidResolverFacade buildDidResolverFacade(final MockServer mockServer) {
        final UrlRewriteProperties urlRewriteProperties = new UrlRewriteProperties();
        urlRewriteProperties.setUrlMappings(Map.of(REGISTRY_ORIGIN, mockServer.getUrl()));

        final DidResolverWebClient webClient = new DidResolverWebClient(RestClient.builder());
        final DidResolverAdapter adapter = new DidResolverAdapter(webClient, new ObjectMapper());
        return new DidResolverFacade(adapter, urlRewriteProperties);
    }
}
