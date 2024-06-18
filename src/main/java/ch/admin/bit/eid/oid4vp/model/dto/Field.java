package ch.admin.bit.eid.oid4vp.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class Field {
    /**
     * The fields object MUST contain a path property. The value of this property MUST be an array of one or more JSONPath string expressions (as defined in the JSONPath Syntax Definition section) that select a target value from the input. The array MUST be evaluated from 0-index forward, breaking as soon as a Field Query Result is found (as described in Input Evaluation), which will be used for the rest of the entry’s evaluation. The ability to declare multiple expressions in this way allows the Verifier to account for format differences - for example: normalizing the differences in structure between JSON-LD/JWT-based Verifiable Credentials and vanilla JSON Web Tokens (JWTs) [RFC7519].
     * The fields object MAY contain an id property. If present, its value MUST be a string that is unique from every other field object’s id property, including those contained in other Input Descriptor Objects.
     * The fields object MAY contain a purpose property. If present, its value MUST be a string that describes the purpose for which the field is being requested.
     * The fields object MAY contain a name property. If present, its value MUST be a string, and SHOULD be a human-friendly name that describes what the target field represents.
     * The fields object MAY contain a filter property, and if present its value MUST be a JSON Schema descriptor used to filter against the values returned from evaluation of the JSONPath string expressions in the path array.
     * The fields object MAY contain an optional property. The value of this property MUST be a boolean, wherein true indicates the field is optional, and false or non-presence of the property indicates the field is required. Even when the optional property is present, the value located at the indicated path of the field MUST validate against the JSON Schema filter, if a filter is present.
     */

    // JSONPath string expressions
    @NotEmpty
    private List<String> path;

    // optional
    private String id;

    // optional
    private String name;

    // optional
    private String purpose;

    // TODO other fields are currently ignored -> check https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor
}
