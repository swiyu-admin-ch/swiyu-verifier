package ch.admin.bj.swiyu.verifier.oid4vp.api.requestobject;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "VerifierMetadata")
public class VerifierMetadataDto {
    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("logo_uri")
    private String logoUri;
}
