package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.enums.ResponseErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseData implements Serializable {

    @Id
    private UUID id;

    private ResponseErrorCodeEnum errorCode;

    private String credentialSubjectData;

    private long expirationInSeconds;
}

