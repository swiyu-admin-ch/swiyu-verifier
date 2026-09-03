package ch.admin.bj.swiyu.verifier.pact;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.domain.CredentialEvaluation;
import ch.admin.bj.swiyu.verifier.domain.IssuerTrustMarker;
import ch.admin.bj.swiyu.verifier.domain.VerificationResultData;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Provider("swiyu-verifier")
@PactFolder
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
class VerifierManagementPactProviderTest {

    private static final String VERIFICATIONS_PATH = "/management/api/verifications";

    @Autowired
    private MockMvc mockMvc;
    @LocalServerPort
    private int serverPort;
    @Autowired
    private ManagementRepository managementRepository;

    @BeforeEach
    void prepareInteraction(final PactVerificationContext context) {
        // Pact 4.7.5's MockMvc target is not binary-compatible with Spring 7; state setup still uses MockMvc.
        context.setTarget(new HttpTestTarget("localhost", serverPort));
        managementRepository.deleteAll();
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(final PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("verification creation is available")
    Map<String, Object> verificationCreationIsAvailable() {
        return Map.of();
    }

    @State("a pending verification exists")
    Map<String, Object> aPendingVerificationExists() throws Exception {
        return createVerification(false, false);
    }

    @State("a successful verification exists")
    Map<String, Object> aSuccessfulVerificationExists() throws Exception {
        return createVerification(false, true);
    }

    @State("a successful redirected verification exists")
    Map<String, Object> aSuccessfulRedirectedVerificationExists() throws Exception {
        return createVerification(true, true);
    }

    @State("no verification exists")
    Map<String, Object> noVerificationExists() {
        return Map.of("verificationId", UUID.randomUUID().toString());
    }

    private Map<String, Object> createVerification(final boolean redirected,
                                                   final boolean successful) throws Exception {
        final MvcResult result = mockMvc.perform(post(VERIFICATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationCreationPayload(redirected)))
                .andExpect(status().isOk())
                .andReturn();

        final String verificationId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        final var management = managementRepository.findById(UUID.fromString(verificationId)).orElseThrow();

        if (successful) {
            management.claimForProcessing();
            management.verificationDone(VerificationResultData.builder()
                    .verifiedResponsesJsonString("""
                            {
                              "VerifiableCredential": {
                                "given_name": "John",
                                "family_name": "Doe"
                              }
                            }
                            """)
                    .evaluations(Map.of("VerifiableCredential", List.of(CredentialEvaluation.builder()
                            .trustMarkers(IssuerTrustMarker.builder().isTrusted(true).build())
                            .build())))
                    .build());
            managementRepository.saveAndFlush(management);
        }

        if (redirected) {
            return Map.of(
                    "verificationId", verificationId,
                    "responseCode", management.getResponseCode().toString());
        }
        return Map.of("verificationId", verificationId);
    }

    private String verificationCreationPayload(final boolean redirected) {
        final String redirectUri = redirected
                ? "\"https://business-verifier.example.com/callback?session_nonce=pact-session\""
                : "null";
        return """
                {
                  "accepted_issuer_dids": ["did:example:issuer"],
                  "trust_anchors": null,
                  "jwt_secured_authorization_request": false,
                  "response_mode": "direct_post",
                  "configuration_override": null,
                  "dcql_query": {
                    "credentials": [
                      {
                        "id": "VerifiableCredential",
                        "format": "vc+sd-jwt",
                        "meta": {
                          "vct_values": ["https://issuer.example.com/vct/test"]
                        },
                        "claims": [
                          {
                            "path": ["name"]
                          }
                        ],
                        "require_cryptographic_holder_binding": true
                      }
                    ],
                    "credential_sets": []
                  },
                  "verification_purpose": null,
                  "redirect_uri": %s
                }
                """.formatted(redirectUri);
    }
}
