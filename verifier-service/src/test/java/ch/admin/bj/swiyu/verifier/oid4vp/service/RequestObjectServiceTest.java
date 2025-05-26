/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.text.ParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class RequestObjectServiceTest {
    private static final UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    RequestObjectService requestObjectService;

    @Autowired
    private ManagementRepository managementRepository;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        // Insert test data programmatically
        var management = Management.builder()
                .id(requestId)
                .jwtSecuredAuthorizationRequest(true)
                .requestNonce("P2vZ8DKAtTuCIU1M7daWLA65Gzoa76tL")
                .state(PENDING)
                .requestedPresentation(presentationDefinition())
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids("TEST_ISSUER_ID")
                .build();
        managementRepository.save(management);
    }

    @Test
    void requestObjectTest() throws ParseException, JsonProcessingException {
        var requestObject = requestObjectService.assembleRequestObject(requestId);

        assertThat(requestObject).isNotNull();
        if (requestObject instanceof String) {

            SignedJWT jwt = SignedJWT.parse((String) requestObject);
            assertThat(jwt).isNotNull();
            assertThat(jwt.getJWTClaimsSet()).isNotNull();
            var header = jwt.getHeader();
            assertThat(header).isNotNull();
            assertThat(header.getType().toString()).isEqualTo("oauth-authz-req+jwt");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String[] chunks = ((String) requestObject).split("\\.");

            var payload = new String(decoder.decode(chunks[1]));
            var payloadMap = objectMapper.readValue(payload, HashMap.class);
            assertThat(payloadMap).isNotNull();
            assertThat(payloadMap.get("version")).isEqualTo("1.0");
        }
    }

    private PresentationDefinition presentationDefinition() throws JsonProcessingException {
        return objectMapper.readValue(presentationDefinitionJson(), PresentationDefinition.class);
    }

    private static String presentationDefinitionJson() {
        return """
                {
                  "id": "cf244758-00f9-4fa0-83ff-6719bac358a2",
                  "name": "Presentation Definition Name",
                  "purpose": "Presentation Definition Purpose",
                  "input_descriptors": [
                    {
                      "id": "test_descriptor_id",
                      "purpose": "Input Descriptor Purpose",
                      "format": {
                        "vc+sd-jwt": {
                          "sd-jwt_alg_values": ["ES256"],
                          "kb-jwt_alg_values": ["ES256"]
                        }
                      },
                      "name": "Test Descriptor Name",
                      "constraints": {
                        "fields": [
                          {
                            "path": ["$"]
                          }
                        ]
                      }
                    }
                  ]
                }
                """;
    }
}
