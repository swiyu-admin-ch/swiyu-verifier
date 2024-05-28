package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.models.entities.dif.DIFPresentationSubmission;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

// TODO check where to add this
@RedisHash("AuthorizationResponseData")
@Data
public class AuthorizationResponseData {

    @Id
    private String id;

    private String state;

    // TODO check
    // vp_token: str | dict | list[str | dict] | None = None
    private String vp_token;


    private DIFPresentationSubmission presentation_submission;

    private String error_description;

    // TODO check if enum
    private String error_code;
}
