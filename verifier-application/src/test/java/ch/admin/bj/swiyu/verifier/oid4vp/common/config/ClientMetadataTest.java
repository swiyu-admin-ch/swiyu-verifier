/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

import ch.admin.bj.swiyu.verifier.api.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
class ClientMetadataTest {
    @Autowired
    OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;

    @Test
    void testClientMetadataConfiguration() {

        OpenidClientMetadataDto clientMetadata = openIdClientMetadataConfiguration.getOpenIdClientMetadata();

        assertEquals("1.0", clientMetadata.getVersion());
    }
}