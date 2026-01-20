package ch.admin.bj.swiyu.verifier.infrastructure.scheduler;

import ch.admin.bj.swiyu.verifier.common.config.WebhookProperties;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEvent;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEventRepository;
import ch.admin.bj.swiyu.verifier.service.callback.CallbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackDispatchScheduler {

    private final WebhookProperties webhookProperties;
    private final CallbackEventRepository callbackEventRepository;
    private final WebClient webClient;

    @Scheduled(initialDelay = 0, fixedDelayString = "${webhook.callback-interval}")
    @Transactional
    public void triggerProcessCallback() {
        if (StringUtils.isBlank(webhookProperties.getCallbackUri())) {
            // No Callback URI defined; We do not need to do callbacks
            return;
        }
        var events = callbackEventRepository.findAll();
        events.forEach(event -> processCallbackEvent(
                event,
                webhookProperties.getCallbackUri(),
                webhookProperties.getApiKeyHeader(),
                webhookProperties.getApiKeyValue()
        ));
    }

    private void processCallbackEvent(CallbackEvent event, String callbackUri, String authHeader, String authValue) {
        // Send the event
        var request =   webClient.post().uri(callbackUri);
        if (!StringUtils.isBlank(authHeader)) {
            request = request.header(authHeader, authValue);
        }
        try {
            request
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(CallbackMapper.toWebhookCallbackDto(event))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            callbackEventRepository.delete(event);
        } catch (WebClientResponseException e) {
            // Note; If delivery failed we will keep retrying to send the message ad-infinitum.
            // This is intended behaviour as we have to guarantee an at-least-once delivery.
            log.error(
                    "Callback to {} failed with status code {} with message {}",
                    webhookProperties.getCallbackUri(),
                    e.getStatusCode(),
                    e.getMessage()
            );
        }
    }
}