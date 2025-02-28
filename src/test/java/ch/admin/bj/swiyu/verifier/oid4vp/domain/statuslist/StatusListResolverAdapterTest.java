/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@RestClientTest(StatusListResolverAdapter.class)
class StatusListResolverAdapterTest {

    private StatusListResolverAdapter statusListResolverAdapter;

    private MockRestServiceServer mockServer;

    private RestClient.Builder builder;

    @MockitoBean
    private UrlRewriteProperties urlRewriteProperties;

    @MockitoBean
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(this.builder).build();
        statusListResolverAdapter = new StatusListResolverAdapter(urlRewriteProperties, this.builder);
    }

    @Test
    void testValidateStatusListSize_ExceedsMaxSize() {
        var url = "https://example.com/statuslist";

        // Check with content size of 10 MB + 1 byte
        this.mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(String.valueOf(10485761L), MediaType.APPLICATION_JSON)
                        .header("Content-Length", String.valueOf(10485761L)));

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list size from " + url + " exceeds maximum allowed size", exception.getMessage());
    }
}
