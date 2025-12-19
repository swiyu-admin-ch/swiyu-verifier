package ch.admin.bj.swiyu.verifier.service.callback;

import ch.admin.bj.swiyu.verifier.common.config.WebhookProperties;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEvent;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEventRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallbackEventProducer {

    private final WebhookProperties webhookProperties;
    private final CallbackEventRepository callbackEventRepository;

    @Transactional
    public void produceEvent(UUID verificationId) {
        if (StringUtils.isBlank(webhookProperties.getCallbackUri())) {
            return;
        }
        var event = CallbackEvent.builder()
                .verificationId(verificationId)
                .timestamp(Instant.now())
                .build();
        callbackEventRepository.save(event);
    }
}

