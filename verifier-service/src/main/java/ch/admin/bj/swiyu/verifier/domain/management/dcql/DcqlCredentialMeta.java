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
     * List of acceptable W3C credential types.
     * Currently not supported.
     */
    @JsonProperty("type_values")
    private List<List<String>> typeValues;

    /**
     * List of acceptable verifiable credential types (vct) values for sd-jwt vc format.
     */
    @JsonProperty("vct_values")
    private List<String> vctValues;

    /**
     * Acceptable ISO/IEC 18013-5 mDOCs.
     * Currently not supported.
     */
    @JsonProperty("doctype_value")
    private String doctypeValue;
}
