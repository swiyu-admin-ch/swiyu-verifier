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
 * Domain model for Digital Credentials Query Language (DCQL) query storage.
 * This represents the complete DCQL query structure that can be stored in the database
 * as JSON and used for DCQL-based verification requests.
 * 
 * According to OpenID for Verifiable Presentations 1.0, Section 6.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcqlQuery {

    /**
     * A non-empty array of Credential Queries that specify the requested Credentials.
     */
    @JsonProperty("credentials")
    private List<DcqlCredential> credentials;

    /**
     * An optional non-empty array of Credential Set Queries that specifies additional
     * constraints on which of the requested Credentials to return.
     */
    @JsonProperty("credential_sets")
    private List<DcqlCredentialSet> credentialSets;
}
