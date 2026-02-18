package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.common.config;

import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Transactional
class ClientMetadataTest {
    @Autowired
    OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;

    @Test
    void testClientMetadataConfiguration() {

        OpenidClientMetadataDto clientMetadata = openIdClientMetadataConfiguration.getOpenIdClientMetadata();

        assertEquals("1.0", clientMetadata.getVersion());
    }
}