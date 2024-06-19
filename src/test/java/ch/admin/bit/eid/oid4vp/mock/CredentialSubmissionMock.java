package ch.admin.bit.eid.oid4vp.mock;

import ch.admin.bit.eid.oid4vp.model.Descriptor;
import ch.admin.bit.eid.oid4vp.model.PresentationSubmission;
import lombok.experimental.UtilityClass;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@UtilityClass
public class CredentialSubmissionMock {

    private static final String DEFAULT_FORMAT = "ldp_vc";

    public static final String CredentialPath = String.format("$.verifiableCredential[%s].credentialSubject",0);

    public static final String CredentialPathList = String.format("$[%s].verifiableCredential[%s].credentialSubject",0 ,0);

    public static PresentationSubmission getPresentationDefinitionMock(final int numberOfDescriptors,
                                                                       final Boolean isList) {

        return getPresentationDefinitionMockWithFormat(numberOfDescriptors, isList, DEFAULT_FORMAT);
    }

    public static PresentationSubmission getPresentationDefinitionMockWithFormat(final int numberOfDescriptors,
                                                                       final Boolean isList, final String format) {
        List<Descriptor> descriptorList = new ArrayList<>();
        for (int i = 0; i < numberOfDescriptors; i++) {
            descriptorList.add(Descriptor.builder()
                    .format(format)
                    .path(String.format("%s.verifiableCredential[%s].credentialSubject", isList ? "$[0]" : "$", i))
                    .build());
        }

        return PresentationSubmission.builder()
                .id(UUID.randomUUID().toString())
                .descriptorMap(descriptorList)
                .build();
    }
}
