package ch.admin.bit.eid.verifier_management.models.dto;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Getter
public class InputDescriptorDto {

    private UUID id;

    private String name;

    private List<String> group;

    private HashMap<String, Object> format;

    private HashMap<String, Object> constraints;
}
