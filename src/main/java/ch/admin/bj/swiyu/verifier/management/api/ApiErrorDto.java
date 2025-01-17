package ch.admin.bj.swiyu.verifier.management.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

@Schema(name = "ApiError")
public record ApiErrorDto(
        @Schema(description = "HTTP status code of the error")
        @NotNull HttpStatus status,
        @Schema(description = "Error message")
        @NotNull String detail
) {
}

