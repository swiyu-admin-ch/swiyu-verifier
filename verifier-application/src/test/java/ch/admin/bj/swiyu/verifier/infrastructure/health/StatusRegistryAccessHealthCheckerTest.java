package ch.admin.bj.swiyu.verifier.infrastructure.health;

import org.apache.tomcat.util.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mockserver.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class StatusRegistryAccessHealthCheckerTest {
    @Container
    private static final MockServerContainer mockServerContainer =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:latest"));

    @Mock
    private ResponseEntity<String> response;
    private MockServerClient mockServerClient;

    private URI uri;
    private StatusRegistryAccessHealthChecker statusRegistryAccessHealthChecker;

    @BeforeEach
    void setUp() throws URISyntaxException {
        this.mockServerClient = new MockServerClient(mockServerContainer.getHost(), mockServerContainer.getServerPort());

        this.uri = new URI("http://%s:%d".formatted(mockServerContainer.getHost(), mockServerClient.getPort()));
        var webClient = WebClient.create();
        this.statusRegistryAccessHealthChecker = new StatusRegistryAccessHealthChecker(webClient, List.of(this.uri));
    }

    @Test
    void performCheck_shouldReturnUp_WhenAllUrlsAvailable() throws Exception {
        this.mockServerClient.when(request().withMethod(Method.GET).withPath("")).respond(response().withStatusCode(HttpStatus.OK.value()));
        this.mockServerClient.hasStarted();

        var builder = Health.unknown();
        this.statusRegistryAccessHealthChecker.performCheck(builder);
        var health = builder.build();

        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails());
        assertNotNull(health.getDetails().get(uri.toString()));
        assertEquals(Status.UP, (Status) health.getDetails().get(uri.toString()));
    }

    @Test
    void performCheck_shouldReturnDown_WhenUrlsReturnsError() throws Exception {
        this.mockServerClient.when(request().withMethod(Method.GET).withPath("")).respond(response().withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        this.mockServerClient.hasStarted();

        var builder = Health.unknown();
        this.statusRegistryAccessHealthChecker.performCheck(builder);
        var health = builder.build();

        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails());
        assertNotNull(health.getDetails().get(uri.toString()));
        assertEquals(Status.DOWN, (Status) health.getDetails().get(uri.toString()));
    }
}
