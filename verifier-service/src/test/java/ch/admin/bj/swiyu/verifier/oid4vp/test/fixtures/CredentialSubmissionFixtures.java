/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures;

import ch.admin.bj.swiyu.verifier.dto.submission.DescriptorDto;
import ch.admin.bj.swiyu.verifier.dto.submission.PresentationSubmissionDto;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@UtilityClass
public class CredentialSubmissionFixtures {

    private static final String DEFAULT_FORMAT = "vc+sd-jwt";

    public static final String CredentialPathList = String.format("$[%s].verifiableCredential[%s].credentialSubject", 0, 0);

    public static PresentationSubmissionDto presentationSubmission(final int numberOfDescriptors,
                                                                   final Boolean isList) {

        return presentationSubmissionWithFormat(numberOfDescriptors, isList, DEFAULT_FORMAT);
    }

    public static PresentationSubmissionDto presentationSubmissionWithFormat(final int numberOfDescriptors,
                                                                             final Boolean isList, final String format) {
        List<DescriptorDto> descriptorList = new ArrayList<>();
        for (int i = 0; i < numberOfDescriptors; i++) {
            descriptorList.add(DescriptorDto.builder()
                    .format(format)
                    .path(String.format("%s.verifiableCredential[%s].credentialSubject", isList ? "$[0]" : "$", i))
                    .build());
        }

        return PresentationSubmissionDto.builder()
                .id(UUID.randomUUID().toString())
                .descriptorMap(descriptorList)
                .build();
    }
}
