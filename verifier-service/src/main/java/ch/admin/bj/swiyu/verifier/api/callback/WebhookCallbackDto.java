package ch.admin.bj.swiyu.verifier.api.callback;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(name = "WebhookCallback", description = "Callback transmitting that verification can now be fetched.")
public class WebhookCallbackDto {
    @JsonProperty(value = "verification_id")
    private UUID verificationId;
    @JsonProperty(value = "timestamp")
    private Instant timestamp;
}