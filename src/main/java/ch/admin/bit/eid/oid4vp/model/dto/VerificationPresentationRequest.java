package ch.admin.bit.eid.oid4vp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationPresentationRequest {

    private String vp_token;

    private String presentation_submission;

    private String error;

    private String error_description;
}
