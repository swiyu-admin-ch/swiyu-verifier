/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.admin.bj.swiyu.verifier.common.config.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.common.config.OpenidClientMetadataDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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