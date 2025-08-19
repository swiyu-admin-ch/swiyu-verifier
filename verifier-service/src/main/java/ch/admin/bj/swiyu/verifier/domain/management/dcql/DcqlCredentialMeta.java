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
 * Domain model for DCQL Credential Metadata.
 * Contains metadata constraints for credential queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcqlCredentialMeta {

    /**
     * List of acceptable credential types.
     */
    @JsonProperty("type_values")
    private List<List<String>> typeValues;

    /**
     * List of acceptable verifiable credential types (vct) values.
     */
    @JsonProperty("vct_values")
    private List<String> vctValues;

    /**
     * List of acceptable issuer DIDs.
     */
    @JsonProperty("doctype_value")
    private String doctype_value;
}
