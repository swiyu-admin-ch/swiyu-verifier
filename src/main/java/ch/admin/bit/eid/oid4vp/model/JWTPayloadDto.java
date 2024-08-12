package ch.admin.bit.eid.oid4vp.model;

import lombok.Data;

import java.util.List;

@Data
public class JWTPayloadDto {

    private String iss;
    private Long iat;
    private List<String> _sd;

    // optional values

    private Long exp;
    private Long nbf;
}
