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
 * Domain model for DCQL Credential Query.
 * Represents a single credential query within a DCQL query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcqlCredential {

    /**
     * Unique identifier for this credential query within the DCQL query.
     */
    @JsonProperty("id")
    private String id;

    /**
     * The format of the requested credential (e.g., "dc+sd-jwt", "jwt_vc_json").
     */
    @JsonProperty("format")
    private String format;

    /**
     * Metadata constraints for the credential.
     */
    @JsonProperty("meta")
    private DcqlCredentialMeta meta;

    /**
     * List of claims requested from this credential.
     */
    @JsonProperty("claims")
    private List<DcqlClaim> claims;

    /**
     * Whether cryptographic holder binding is required for this credential.
     */
    @JsonProperty("require_cryptographic_holder_binding")
    private Boolean requireCryptographicHolderBinding;

    /**
     * Whether multiple instances of this credential are allowed.
     */
    @JsonProperty("multiple")
    private Boolean multiple;
}
