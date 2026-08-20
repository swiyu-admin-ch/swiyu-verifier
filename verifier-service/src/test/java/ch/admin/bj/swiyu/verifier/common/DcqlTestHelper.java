package ch.admin.bj.swiyu.verifier.common;

import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlQueryDto;
import tools.jackson.databind.ObjectMapper;

public class DcqlTestHelper {

    public static final String DC_SD_JWT_CREDENTIAL_FORMAT = "dc+sd-jwt";
    @Deprecated(since = "EIDOMNI-179")
    public static final String VC_SD_JWT_CREDENTIAL_FORMAT = "vc+sd-jwt";

    public static DcqlQuery stringToDcqlQuery(String dcqlQuery) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(dcqlQuery, DcqlQuery.class);
    }

    public static DcqlQueryDto stringToDcqlQueryDto(String dcqlQuery) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(dcqlQuery, DcqlQueryDto.class);
    }
}
