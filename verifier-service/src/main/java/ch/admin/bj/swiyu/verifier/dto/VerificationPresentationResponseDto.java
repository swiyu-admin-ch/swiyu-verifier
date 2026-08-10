package ch.admin.bj.swiyu.verifier.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;

/**
 * Data Transfer Object returned after a verification presentation is processed.
 *
 * <p>Provides optional routing information for the business verifier. When present,
 * the {@code redirectURI} is used to route the response back to the business verifier's
 * callback endpoint. The value is serialized as {@code "redirect_uri"} and may be
 * omitted (null) if not set in the initial request.
 */
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