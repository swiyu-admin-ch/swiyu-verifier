/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerificationPresentationDCQLRequest",
        description = "DCQL verification presentation request with VP token object")
public class VerificationPresentationDCQLRequestDto {

    @Schema(
            description = "JSON-encoded object containing entries where the key is the id value used for a Credential " +
                    "Query in the DCQL query and the value is an array of one or more Presentations that match the " +
                    "respective Credential Query. Each Presentation is represented as a string or object, " +
                    "depending on the format.",
            example = """
                    {
                      "my_credential": ["eyJhbGci...QMA"]
                    }
                    """,
            implementation = Map.class
    )
    @JsonProperty("vp_token")
    private Map<String, List<Object>> vpToken;

}
