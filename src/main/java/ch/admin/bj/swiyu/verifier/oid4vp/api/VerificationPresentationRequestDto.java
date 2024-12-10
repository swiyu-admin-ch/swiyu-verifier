package ch.admin.bj.swiyu.verifier.oid4vp.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static org.springframework.util.StringUtils.hasText;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerificationPresentationRequest")
public class VerificationPresentationRequestDto {

    private String vp_token;

    /**
     * Note: Why string - we do not use PresentationSubmissionDto here, because we support invalid
     * submissions. We will parse the JSON and if it is invalid we apply propper
     * error handling (e.g. updating the entity).
     */
    private String presentation_submission;

    private String error;

    private String error_description;

    @JsonIgnore
    public boolean isClientRejection() {
        return hasText(error);
    }
}
