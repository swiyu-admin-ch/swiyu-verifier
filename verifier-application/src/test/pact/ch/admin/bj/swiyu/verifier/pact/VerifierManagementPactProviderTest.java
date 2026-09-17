package ch.admin.bj.swiyu.verifier.pact;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerAuth;
import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

@Provider("swiyu-verifier")
@PactBroker(url = "${PACT_BROKER_BASE_URL}",
        authentication = @PactBrokerAuth(token = "${PACT_BROKER_TOKEN:}"))
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Import(VerifierManagementPactFixture.class)
class VerifierManagementPactProviderTest {

    @Autowired
    private VerifierManagementPactFixture fixture;
    @LocalServerPort
    private int serverPort;

    @BeforeEach
    void prepareInteraction(final PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", serverPort));
        fixture.cleanDatabase();
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
    Map<String, Object> aPendingVerificationExists() {
        return fixture.createVerification(false, false);
    }

    @State("a successful verification exists")
    Map<String, Object> aSuccessfulVerificationExists() {
        return fixture.createVerification(false, true);
    }

    @State("a successful redirected verification exists")
    Map<String, Object> aSuccessfulRedirectedVerificationExists() {
        return fixture.createVerification(true, true);
    }

    @State("no verification exists")
    Map<String, Object> noVerificationExists() {
        return Map.of("verificationId", UUID.randomUUID().toString());
    }
}
