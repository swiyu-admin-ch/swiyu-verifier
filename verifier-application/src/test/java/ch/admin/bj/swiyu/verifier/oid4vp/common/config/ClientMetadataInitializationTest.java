package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ClientMetadataInitializationTest {

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    @MockitoBean
    private Resource clientMetadataResourceMock;

    @Test
    void ClientMetadataInitializationFailedWithMissingVpFormat() throws IOException {
        var underTest = new OpenIdClientMetadataConfiguration(applicationProperties, objectMapper, validator);
        when(clientMetadataResourceMock.getContentAsString(any())).thenReturn("""
                {
                  "client_id": "${VERIFIER_DID}",
                  "client_name#en": "English name (all regions)",
                  "client_name#fr": "French name (all regions)",
                  "client_name#de-DE": "German name (region Germany)",
                  "client_name#de-CH": "German name (region Switzerland)",
                  "client_name#de": "German name (fallback)",
                  "client_name": "Fallback name",
                  "logo_uri": "www.example.com/logo.png",
                  "logo_uri#fr": "www.example.com/logo_fr.png"
                }
                """);
        underTest.setClientMetadataResource(clientMetadataResourceMock);

        assertThatThrownBy(underTest::initOpenIdClientMetadata)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid OpenID client metadata: must not be null, ");
    }
}
