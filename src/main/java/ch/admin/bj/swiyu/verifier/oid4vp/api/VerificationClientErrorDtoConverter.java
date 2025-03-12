package ch.admin.bj.swiyu.verifier.oid4vp.api;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts a string to a {@link VerificationClientErrorDto}. This conversion is needed because default @JsonValue
 * together with JacksonConverter is only used when the mime type is application/json. Because the error and
 * error_description are part of an application/x-www-form-urlencoded request, we need to convert the enum explicitly
 */
@Component
public class VerificationClientErrorDtoConverter implements Converter<String, VerificationClientErrorDto> {
    @NotNull
    @Override
    public VerificationClientErrorDto convert(String source) {
        for (VerificationClientErrorDto error : VerificationClientErrorDto.values()) {
            if (error.toString().equals(source)) { // Match with @JsonValue
                return error;
            }
        }
        throw new IllegalArgumentException("Unknown error value: " + source);
    }
}