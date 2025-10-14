package ch.admin.bj.swiyu.verifier.api.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name= "JWK Set")
public record JwkSetDto(
    @JsonProperty("keys")
    List<JwkDto> keys
){}
