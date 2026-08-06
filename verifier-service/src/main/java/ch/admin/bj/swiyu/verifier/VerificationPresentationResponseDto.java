package ch.admin.bj.swiyu.verifier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationPresentationResponseDto(
        @JsonProperty("redirect_uri")
        @Schema(
                hidden = true,
                description = "Optional: Only provided if set in the initial request. It is used to route the response back to the business verifier's endpoint.",
                example = "https://shop.ch/callback?session_nonce=123&response_code=xyz"
        )
        URI redirectURI
) { }
