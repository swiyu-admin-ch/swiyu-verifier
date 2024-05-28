package ch.admin.bit.eid.verifier_management.models.entities.dif;

import lombok.Data;

@Data
public class DIFPresentationDescriptor {

    private String id;

    // TODO check = Literal['jwt_vp_json']
    private String format;
    /**
    The format the VP is using, we only accept jwt verifiable presentations
    **/
    // Optional[str]
    private String path = "$";


    private DIFPathNested path_nested;
}
