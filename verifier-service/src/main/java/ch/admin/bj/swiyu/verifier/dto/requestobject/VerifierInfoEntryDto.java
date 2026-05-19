package ch.admin.bj.swiyu.verifier.dto.requestobject;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a single entry in the {@code verifier_info} array of the JWT-Secured Authorization Request.
 * <p>
 * As mandated by Trust Protocol 2.0, each trust statement ({@code idTS}, {@code pvaTS}, {@code vqPS})
 * is embedded using exactly this structure: {@code {"format": "jwt", "data": "<jwt-string>"}}.
 * No additional claims (e.g. {@code credential_ids}) must be present.
 */
@Value
@Builder
public class VerifierInfoEntryDto {

    /**
     * The format of the trust statement. Always {@code "jwt"} for TP2.0.
     */
    @JsonProperty("format")
    String format;

    /**
     * The raw compact-serialized JWT of the trust statement.
     */
    @JsonProperty("data")
    String data;

    /**
     * Creates a {@link VerifierInfoEntryDto} with {@code format = "jwt"} and the given JWT data.
     *
     * @param jwtData the compact-serialized JWT string
     * @return a new entry ready for inclusion in the {@code verifier_info} array
     */
    public static VerifierInfoEntryDto ofJwt(String jwtData) {
        return VerifierInfoEntryDto.builder()
                .format("jwt")
                .data(jwtData)
                .build();
    }
}

