/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pact Consumer Test for Verifier Application.
 * This test simulates a consumer (e.g., a wallet or external system) that interacts with the verifier-application.
 * It generates pact files that define the contract expectations.
 */
@ExtendWith(PactConsumerTestExt.class)
class VerificationControllerConsumerPactTest {

    /**
     * Define a pact for getting request object as JSON.
     * This represents the contract: "When I request a request object with a valid request_id, I expect a JSON response with RequestObjectDto structure"
     * Focus on structure validation, not exact values - using type matchers for flexibility.
     * <p>
     * Note: The endpoint can return either JSON or JWT depending on the JAR (JWT secured authorization request) flag.
     * This test covers the JSON response scenario (unsecured request object).
     */
    @Pact(consumer = "wallet-consumer", provider = "swiyu-verifier-application")
    public V4Pact getRequestObjectAsJson(PactDslWithProvider builder) {
        var body = new PactDslJsonBody()
                // Required fields for RequestObjectDto
                .stringType("client_id", "did:example:verifier123")
                .stringType("client_id_scheme", "did")
                .stringType("response_type", "vp_token")
                .stringType("response_mode", "direct_post")
                .stringType("response_uri", "https://verifier.example.com/oid4vp/api/request-object/550e8400-e29b-41d4-a716-446655440000/response-data")
                .stringType("nonce", "n-0S6_WzA2Mj")

                // Optional: presentation_definition (for PE format)
                .object("presentation_definition")
                .stringType("id", "550e8400-e29b-41d4-a716-446655440000")
                .stringType("name", "Test Verification")
                .stringType("purpose", "Test purpose")
                .array("input_descriptors")
                .object()
                .stringType("id", "input_1")
                .closeObject()
                .closeArray()
                .closeObject()

                // Optional: client_metadata
                .object("client_metadata")
                .stringType("client_id", "did:example:verifier123")
                .object("vp_formats")
                .object("jwt_vp")
                .array("alg")
                .stringValue("ES256")
                .closeArray()
                .closeObject()
                .closeObject()
                .closeObject()

                .asBody();

        return builder.given("a valid request object exists for the given request_id")
                .uponReceiving("a request for request object as JSON")
                .path("/oid4vp/api/request-object/550e8400-e29b-41d4-a716-446655440000")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(body)
                .toPact(V4Pact.class);
    }

    /**
     * Test the request object endpoint (JSON response) against the mock server.
     * Verifies that the response structure matches RequestObjectDto expectations.
     */
    @Test
    @PactTestFor(pactMethod = "getRequestObjectAsJson", pactVersion = PactSpecVersion.V4)
    void testGetRequestObjectAsJson(MockServer mockServer) {
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.getForEntity(
                mockServer.getUrl() + "/oid4vp/api/request-object/550e8400-e29b-41d4-a716-446655440000",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("application/json");

        // Verify the response has the expected structure
        assertThat(response.getBody()).contains("client_id");
        assertThat(response.getBody()).contains("response_type");
        assertThat(response.getBody()).contains("response_mode");
        assertThat(response.getBody()).contains("nonce");
    }




}

