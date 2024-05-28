package ch.admin.bit.eid.verifier_management.models.entities.dif;

import lombok.Data;

@Data
public class DIFPathNested {
    private String path;

    /*
    The Format in which the vp token is provided. We only support jwt_vc
    */
    // TODO check Literal['jwt_vc']
    private String format;
}
