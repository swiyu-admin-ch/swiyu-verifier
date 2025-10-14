package ch.admin.bj.swiyu.verifier.api.metadata;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "JsonWebKey")
public record JwkDto(
        @JsonProperty("kty") String kty,
        @JsonProperty("kid") String kid,
        @JsonProperty("use") String use,
        @JsonProperty("alg") String alg,
        @JsonProperty("n") String n,
        @JsonProperty("e") String e,
        @JsonProperty("crv") String crv,
        @JsonProperty("x") String x,
        @JsonProperty("y") String y
) {}

