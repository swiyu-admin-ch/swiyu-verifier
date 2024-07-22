package ch.admin.bit.eid.oid4vp.model.persistence;

import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseData {

    private ResponseErrorCodeEnum errorCode;

    private String credentialSubjectData;
}
