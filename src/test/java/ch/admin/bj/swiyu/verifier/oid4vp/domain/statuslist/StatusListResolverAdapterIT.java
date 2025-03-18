/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.admin.bj.swiyu.verifier.oid4vp.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.StatusListFetchFailedException;
import ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.config.RestClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest({StatusListResolverAdapter.class})
@Import({RestClientConfig.class})
class StatusListResolverAdapterIT {

    private final String url = "https://example.com/statuslist";
    @Autowired
    MockRestServiceServer mockServer;
    @Autowired
    StatusListResolverAdapter statusListResolverAdapter;

    @MockitoBean
    private UrlRewriteProperties urlRewriteProperties;

    @MockitoBean
    private ApplicationProperties applicationProperties;
    @MockitoBean
    private VerificationProperties verificationProperties;

    @BeforeEach
    void setUp() {
        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(url);
    }

    @Test
    void testValidateStatusListSize_ExceedsMaxSize() {

        // Check with content size of 10 MB
        this.mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("Status list content", MediaType.TEXT_PLAIN)
                        .header("Content-Length", String.valueOf(10485760L)));

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list size from " + url + " exceeds maximum allowed size", exception.getMessage());
    }


    @Test
    void testUnresolvableStatusList_thenIllegalArgumentException() {
        this.mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        var exception = assertThrows(StatusListFetchFailedException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list with uri: " + url + " could not be retrieved", exception.getMessage());
    }

    @Test
    void testInvalidDomain_thenIllegalArgumentException() {
        var hosts = List.of("not_example.com");
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(hosts);

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("StatusList %s does not contain a valid host from %s".formatted(url, hosts), exception.getMessage());
    }
}