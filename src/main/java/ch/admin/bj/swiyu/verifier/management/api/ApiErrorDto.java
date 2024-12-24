package ch.admin.bj.swiyu.verifier.management.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

@Schema(name = "ApiError")
public record ApiErrorDto(
        @NotNull HttpStatus status,
        @NotNull String detail
) {
}

