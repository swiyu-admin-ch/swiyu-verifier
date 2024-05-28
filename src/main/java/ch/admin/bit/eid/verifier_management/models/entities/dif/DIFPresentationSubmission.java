package ch.admin.bit.eid.verifier_management.models.entities.dif;

import lombok.Data;

import java.util.List;

@Data
public class DIFPresentationSubmission {

    private String id;
    private String definition_id;
    private List<DIFPresentationDescriptor> descriptor_map;
}
