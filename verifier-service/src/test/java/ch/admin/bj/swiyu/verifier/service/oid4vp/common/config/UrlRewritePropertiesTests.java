/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp.common.config;

import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlRewritePropertiesTests {

    @ParameterizedTest
    @CsvSource({
            "https://status.bit.admin.ch/, https://svcgw-esb:23/",
            "https://bit.admin.ch/, https://bit.admin.ch/",
            "https://status.bit.admin.ch, https://svcgw-esb:23",
            "https://status.bit.admin.ch/my/awesome/url, https://svcgw-esb:23/my/awesome/url",
            "https://identifier.bit.admin.ch/my/awesome/url, https://svcgw-esb:45/my/awesome/url",
            "https://somethingcrazy, https://somethingcrazy"
    })
    void testGetRewrittenUrl_WithMapping_Returns(String original, String mapped) throws JsonProcessingException {
        var config = new UrlRewriteProperties();
        config.setMapping("""
                    {
                    "https://status.bit.admin.ch":"https://svcgw-esb:23",
                    "https://identifier.bit.admin.ch":"https://svcgw-esb:45"
                    }
                """);
        config.init();
        assertEquals(mapped, config.getRewrittenUrl(original));
    }

    @Test
    void testGetRewrittenUrl_WithNoMapping() throws JsonProcessingException {
        var config = new UrlRewriteProperties();
        config.init();
        assertEquals("https://status.bit.admin.ch/", config.getRewrittenUrl("https://status.bit.admin.ch/"));
    }
}
