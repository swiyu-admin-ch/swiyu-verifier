/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
public class ClientMetadataTest {
    @Autowired
    OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;

    @Test
    void testClientMetadataConfiguration() {
        Map<String, Object> clientMetadata = openIdClientMetadataConfiguration.getOpenIdClientMetadata();

        assertEquals("1.0", clientMetadata.get("version"));
    }
}
