package ch.admin.bit.eid.verifier_management.exceptions;

import lombok.Data;

@Data
public class OpenIdError {

    private String error;
    private String error_description;
    private String error_code; //None = None
    private String additional_error_description; //None = None
}
