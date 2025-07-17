package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Transactional
public abstract class BaseVerificationControllerTest {

    protected static final UUID REQUEST_ID_SECURED = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    protected static final UUID REQUEST_ID_SDJWT_MGMT_NO_SIGNATURE = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee1");
    protected static final UUID REQUEST_ID_EXPIRED = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee2");
    protected static final UUID REQUEST_ID_WITHOUT_ACCEPTED_ISSUER = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee3");
    protected static final UUID REQUEST_DIFFERENT_ALGS = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee4");
    protected static final UUID REQUEST_DIFFERENT_KB_ALGS = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee5");

    protected static final String NONCE_SD_JWT_SQL = "P2vZ8DKAtTuCIU1M7daWLA65Gzoa76tL";

    @Autowired
    protected ManagementRepository managementEntityRepository;

    @BeforeEach
    void setUp() throws JsonProcessingException {

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_SDJWT_MGMT_NO_SIGNATURE)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .requestedPresentation(presentationDefinition(presentationDefinitionJson()))
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of("TEST_ISSUER_ID"))
                .jwtSecuredAuthorizationRequest(false)
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_SECURED)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .requestedPresentation(presentationDefinition(presentationDefinitionJson()))
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of("TEST_ISSUER_ID"))
                .jwtSecuredAuthorizationRequest(true)
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_EXPIRED)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .requestedPresentation(presentationDefinition(presentationDefinitionJson()))
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(0)
                .acceptedIssuerDids(List.of("TEST_ISSUER_ID"))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_WITHOUT_ACCEPTED_ISSUER)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .requestedPresentation(presentationDefinition(presentationDefinitionJson()))
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_DIFFERENT_ALGS)
                .jwtSecuredAuthorizationRequest(true)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .requestedPresentation(presentationDefinition(presentationDefinitionJsonDiffAlgs()))
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of("TEST_ISSUER_ID"))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_DIFFERENT_KB_ALGS)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .requestedPresentation(presentationDefinition(presentationDefinitionJsonDiffKbAlgs()))
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of("TEST_ISSUER_ID"))
                .build());
    }

    private PresentationDefinition presentationDefinition(String presentationDefinitionJson) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(presentationDefinitionJson, PresentationDefinition.class);
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
                          "sd-jwt_alg_values": [
                            "ES256"
                          ],
                          "kb-jwt_alg_values": [
                            "ES256"
                          ]
                        }
                      },
                      "name": "Test Descriptor Name",
                      "constraints": {
                        "fields": [
                          {
                            "path": [
                              "$"
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """;
    }

    private static String presentationDefinitionJsonDiffAlgs() {
        return """
                {
                  "id": "cf244758-00f9-4fa0-83ff-6719bac358a2",
                  "name": null,
                  "purpose": "string",
                  "input_descriptors": [
                    {
                      "id": "test_descriptor_id",
                      "format": {
                        "vc+sd-jwt": {
                          "sd-jwt_alg_values": [
                            "SHA256"
                          ],
                          "kb-jwt_alg_values": [
                            "ES256"
                          ]
                        }
                      },
                      "name": "Test Descriptor Name",
                      "constraints": {
                        "fields": [
                          {
                            "path": [
                              "$"
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """;
    }

    private static String presentationDefinitionJsonDiffKbAlgs() {
        return """
                {
                  "id": "cf244758-00f9-4fa0-83ff-6719bac358a2",
                  "name": null,
                  "purpose": "string",
                  "input_descriptors": [
                    {
                      "id": "test_descriptor_id",
                      "format": {
                        "vc+sd-jwt": {
                          "sd-jwt_alg_values": [
                            "ES256"
                          ],
                          "kb-jwt_alg_values": [
                            "SHA256"
                          ]
                        }
                      },
                      "name": "Test Descriptor Name",
                      "constraints": {
                        "fields": [
                          {
                            "path": [
                              "$"
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """;
    }

}