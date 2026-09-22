package ch.admin.bj.swiyu.verifier.domain.management.dcql;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import ch.admin.bj.swiyu.verifier.service.dcql.DcqlUtil;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A Trusted Authorities Query is an object representing information that helps to identify 
 * an authority or the trust framework that certifies Issuers. 
 * A Credential is identified as a match to a Trusted Authorities Query if it matches with
 *  one of the provided values in one of the provided types. How exactly the matching works is defined by the type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedAuthority {
    @JsonProperty("type")
    @Builder.Default
    private String type = DcqlUtil.TRUSTED_AUTHORITY_TYPE_DID;

    @JsonProperty("values")
    @NotEmpty 
    private List<String> values;
}
