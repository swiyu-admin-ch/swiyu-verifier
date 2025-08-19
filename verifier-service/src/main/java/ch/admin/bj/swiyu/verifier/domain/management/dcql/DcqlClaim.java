/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.domain.management.dcql;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Domain model for DCQL Claim.
 * Represents a specific claim request within a credential query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcqlClaim {

    @JsonProperty("id")
    private String id;

    /**
     * JSON path to the claim within the credential.
     */
    @JsonProperty("path")
    private List<String> path;

    @JsonProperty("values")
    List<String> values; // Todo: should not be object
}
