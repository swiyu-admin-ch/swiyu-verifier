package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static ch.admin.bj.swiyu.verifier.service.management.fixtures.ManagementFixtures.management;
import static ch.admin.bj.swiyu.verifier.service.management.ManagementMapper.toManagementResponseDto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.util.CollectionUtils.isEmpty;

class ManagementMapperTest {

    private static final String EXTERNAL_URL = "https://example.com/oid4vp";
    private static final String CLIENT_ID = "client_id";
    private static final String SWIYU_VERIFIER = "openid4vp";

    @Mock
    ApplicationProperties applicationProperties;

    @BeforeEach
    void setUp() {
        applicationProperties = mock(ApplicationProperties.class);
        when(applicationProperties.getExternalUrl()).thenReturn(EXTERNAL_URL);
        when(applicationProperties.getClientId()).thenReturn(CLIENT_ID);
        when(applicationProperties.getDeeplinkSchema()).thenReturn(SWIYU_VERIFIER);
    }

    @Test
    void toManagementResponseDtoTest() {
        // GIVEN
        var mgmt = management();
        var expectedVerificationUrl = "%s/oid4vp/api/request-object/%s".formatted(EXTERNAL_URL, mgmt.getId());
        var expectedDeeplink = getExpectedVerificationDeeplink(SWIYU_VERIFIER, CLIENT_ID, expectedVerificationUrl);
        // WHEN
        var dto = toManagementResponseDto(mgmt, applicationProperties);
        // THEN
        assertNotNull(dto);
        assertEquals(mgmt.getId(), dto.id());
        assertThat(dto.requestNonce()).isNotBlank();
        assertEquals(mgmt.getState().toString(), dto.state().toString());
        assertNull(dto.walletResponse());
        assertEquals(expectedVerificationUrl, dto.verificationUrl());
        assertEquals(expectedDeeplink, dto.verificationDeeplink());
        // check if correct deeplink schema
        assertTrue(expectedDeeplink.startsWith("%s://?client_id".formatted(SWIYU_VERIFIER)));
    }

    @Test
    void toManagementResponseDtoTest_NullManagement() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            toManagementResponseDto(null, applicationProperties);
        });

        assertEquals("Management must not be null", exception.getMessage());
    }

    private String getExpectedVerificationDeeplink(String deeplinkSchema, String clientId, String requestUri) {
        var urlEncodedClientId = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        var urlEncodedRequestUri = URLEncoder.encode(requestUri, StandardCharsets.UTF_8);
        return "%s://?client_id=%s&request_uri=%s".formatted(deeplinkSchema, urlEncodedClientId, urlEncodedRequestUri);
    }

}