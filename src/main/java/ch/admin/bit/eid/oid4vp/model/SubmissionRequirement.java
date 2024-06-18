package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.model.validations.ValidSubmissionRequirement;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@ValidSubmissionRequirement
public class SubmissionRequirement {

    @NotNull
    @Pattern(regexp="^(all|pick)$",message="Invalid rule must be either pick or all")
    private String rule; // all or pick

    @NotNull
    private String from;

    @NotNull
    @JsonProperty("from_nested")
    private List<SubmissionRequirement> fromNested;

    private String name;

    private String purpose;

    // pick only
    private Integer count;

    private Integer min;

    private Integer max;
}
