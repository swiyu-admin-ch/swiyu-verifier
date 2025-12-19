package ch.admin.bj.swiyu.verifier.service.callback;

import ch.admin.bj.swiyu.verifier.dto.callback.WebhookCallbackDto;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEvent;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CallbackMapper {
    public static WebhookCallbackDto toWebhookCallbackDto(CallbackEvent event) {
        return WebhookCallbackDto.builder()
                .verificationId(event.getVerificationId())
                .timestamp(event.getTimestamp()).build();
    }
}
