package ch.admin.bj.swiyu.verifier.infrastructure.scheduler;

import ch.admin.bj.swiyu.verifier.common.config.WebhookProperties;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEvent;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEventRepository;
import ch.admin.bj.swiyu.verifier.service.callback.CallbackEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CallbackTest {
    @Mock
    private WebhookProperties webhookProperties;
    @Mock
    private CallbackEventRepository callbackEventRepository;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @InjectMocks
    private CallbackEventProducer callbackEventProducer;

    @InjectMocks
    private CallbackDispatchScheduler callbackDispatchScheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(webhookProperties.getCallbackUri()).thenReturn("http://test/callback");
        when(webhookProperties.getApiKeyHeader()).thenReturn("x-api-key");
        when(webhookProperties.getApiKeyValue()).thenReturn("secret");
    }

    @Test
    void produceEvent_savesEvent() {
        UUID id = UUID.randomUUID();
        callbackEventProducer.produceEvent(id);
        verify(callbackEventRepository).save(any(CallbackEvent.class));
    }

    /**
     * this test verifies that the triggerProcessCallback method sends the callback event.
     * The event must be deleted from the outbox.
     */
    @Test
    void triggerProcessCallback_sendsAndDeletesEvent() {
        CallbackEvent event = CallbackEvent.builder()
                .id(UUID.randomUUID())
                .verificationId(UUID.randomUUID())
                .timestamp(Instant.now())
                .build();
        when(callbackEventRepository.findAll()).thenReturn(List.of(event));
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(Object.class))).thenReturn(requestHeadersSpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        callbackDispatchScheduler.triggerProcessCallback();

        verify(webClient).post();
        verify(callbackEventRepository).delete(event);
    }

    /**
     * This test verifies that if the WebClient throws an exception.
     * The event is not deleted, to be retried at a later point.
     */
    @Test
    void triggerProcessCallback_handlesWebClientException() {
        CallbackEvent event = CallbackEvent.builder()
                .id(UUID.randomUUID())
                .verificationId(UUID.randomUUID())
                .timestamp(Instant.now())
                .build();
        when(callbackEventRepository.findAll()).thenReturn(List.of(event));
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(Object.class))).thenReturn(requestHeadersSpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.error(WebClientResponseException.create(500, "Internal Server Error", null, null, null))
        );
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        callbackDispatchScheduler.triggerProcessCallback();

        verify(callbackEventRepository, never()).delete(event);
    }
}