package ch.admin.bj.swiyu.verifier.service.trustregistry.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.matchingrules.MinTypeMatcher;
import au.com.dius.pact.core.model.matchingrules.RegexMatcher;
import ch.admin.bj.swiyu.core.trust.client.model.PagedModelString;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.COMPACT_JWT_REGEX;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.CONSUMER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.ISSUER_DID;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.PROVIDER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.VCT_ELFA;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.VCT_MDL;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.buildApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PactConsumerTest
@PactTestFor(providerName = PROVIDER, pactVersion = PactSpecVersion.V4)
class ProtectedIssuanceAuthorizationTrustStatementConsumerPactTest {

    private static final String PATH = "/api/v2/protected-issuance-authorization-trust-statement/";
    private static final String QUERY = "sub=" + ISSUER_DID + "&filterActive=true";
    private static final String ISSUER_IDENTIFIER = "issuerIdentifier";

    private static final String PIA_TS_ELFA_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN3aXl1LXByb3RlY3RlZC1pc3N1YW5jZS1hdXRob3JpemF0aW9uLXRydXN0LXN0YXRlbWVudCtqd3QifQ."
                    + "eyJleHAiOjQxMDI0NDQ4MDAsInN1YiI6ImRpZDp3ZWJ2aDpRbVl5UVNvMWMxWW03b3JXeExZdkNyelJMWmFkNVp4UThIa0JMeUVFNFJSQkIxOmlkZW50aWZpZXIuYWRtaW4uY2g6YXBpOnYxOmRpZCIsImNhbl9pc3N1ZSI6eyJ2Y3QiOiJodHRwczovL2V4YW1wbGUuY2gvdmN0L2VsZmEifX0."
                    + "cGFjdC10ZXN0LXNpZ25hdHVyZQ";

    private static final String PIA_TS_MDL_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN3aXl1LXByb3RlY3RlZC1pc3N1YW5jZS1hdXRob3JpemF0aW9uLXRydXN0LXN0YXRlbWVudCtqd3QifQ."
                    + "eyJleHAiOjQxMDI0NDQ4MDAsInN1YiI6ImRpZDp3ZWJ2aDpRbVl5UVNvMWMxWW03b3JXeExZdkNyelJMWmFkNVp4UThIa0JMeUVFNFJSQkIxOmlkZW50aWZpZXIuYWRtaW4uY2g6YXBpOnYxOmRpZCIsImNhbl9pc3N1ZSI6eyJ2Y3QiOiJodHRwczovL2V4YW1wbGUuY2gvdmN0L21kbCJ9fQ."
                    + "cGFjdC10ZXN0LXNpZ25hdHVyZQ";

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact activeProtectedIssuanceAuthorizationTrustStatements(final PactDslWithProvider builder) {
        return builder
                .given("active Protected Issuance Authorization Trust Statements are registered for an issuer",
                        Map.of(
                                ISSUER_IDENTIFIER, ISSUER_DID,
                                "vctValues", List.of(VCT_ELFA, VCT_MDL)))
                .uponReceiving("GET active Protected Issuance Authorization Trust Statements")
                .method("GET")
                .path(PATH)
                .query(QUERY)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "^application/json(?:;\\s*charset=[^;]+)?$", "application/json")
                .body(nonEmptyContentBody())
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact emptyProtectedIssuanceAuthorizationTrustStatements(final PactDslWithProvider builder) {
        return builder
                .given("no active Protected Issuance Authorization Trust Statement is registered for an issuer",
                        Map.of(ISSUER_IDENTIFIER, ISSUER_DID))
                .uponReceiving("GET active Protected Issuance Authorization Trust Statements when none are registered")
                .method("GET")
                .path(PATH)
                .query(QUERY)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "^application/json(?:;\\s*charset=[^;]+)?$", "application/json")
                .body(emptyContentBody())
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact unavailableProtectedIssuanceAuthorizationTrustStatements(final PactDslWithProvider builder) {
        return builder
                .given("Protected Issuance Authorization Trust Statement retrieval is unavailable",
                        Map.of(ISSUER_IDENTIFIER, ISSUER_DID))
                .uponReceiving("GET Protected Issuance Authorization Trust Statements while retrieval is unavailable")
                .method("GET")
                .path(PATH)
                .query(QUERY)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(500)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "activeProtectedIssuanceAuthorizationTrustStatements")
    void shouldDeserializeAllActiveProtectedIssuanceAuthorizationTrustStatements(final MockServer mockServer) {
        final PagedModelString response = buildApi(mockServer)
                .listPiaTS(ISSUER_DID, true, null, null, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getContent())
                .containsExactlyInAnyOrder(PIA_TS_ELFA_JWT, PIA_TS_MDL_JWT);
    }

    @Test
    @PactTestFor(pactMethod = "emptyProtectedIssuanceAuthorizationTrustStatements")
    void shouldDeserializeEmptyProtectedIssuanceAuthorizationTrustStatementList(final MockServer mockServer) {
        final PagedModelString response = buildApi(mockServer)
                .listPiaTS(ISSUER_DID, true, null, null, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    @PactTestFor(pactMethod = "unavailableProtectedIssuanceAuthorizationTrustStatements")
    void shouldPropagateInternalServerErrorWhenProtectedIssuanceAuthorizationTrustStatementRetrievalIsUnavailable(
            final MockServer mockServer) {
        assertThatThrownBy(() -> buildApi(mockServer)
                .listPiaTS(ISSUER_DID, true, null, null, null)
                .block())
                .isInstanceOf(WebClientResponseException.InternalServerError.class);
    }

    private static PactDslJsonBody nonEmptyContentBody() {
        final PactDslJsonBody body = new PactDslJsonBody();
        body.array("content")
                .stringValue(PIA_TS_ELFA_JWT)
                .stringValue(PIA_TS_MDL_JWT)
                .closeArray();
        body.getMatchers()
                .addRule("$.content", new MinTypeMatcher(1))
                .addRule("$.content[*]", new RegexMatcher(COMPACT_JWT_REGEX));
        return body;
    }

    private static PactDslJsonBody emptyContentBody() {
        final PactDslJsonBody body = new PactDslJsonBody();
        body.array("content").closeArray();
        return body;
    }
}
