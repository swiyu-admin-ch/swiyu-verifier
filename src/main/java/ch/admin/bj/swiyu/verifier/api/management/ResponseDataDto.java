/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api.management;

import ch.admin.bj.swiyu.verifier.api.VerificationErrorResponseCodeDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(name = "ResponseData")
public record ResponseDataDto(

        @JsonProperty("error_code")
        VerificationErrorResponseCodeDto errorCode,

        @JsonProperty("error_description")
        String errorDescription,

        @JsonProperty("credential_subject_data")
        Map<String, Object> credentialSubjectData

) {
}
