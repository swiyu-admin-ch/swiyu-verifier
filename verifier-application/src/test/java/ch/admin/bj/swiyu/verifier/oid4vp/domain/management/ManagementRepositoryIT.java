/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.management;

import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest()
@Testcontainers
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Transactional
class ManagementRepositoryIT {

    @Autowired
    private ManagementRepository managementEntityRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static String presentationDefinitionJson() {
        return """
                {
                  "id": "31f12873-72df-481c-b0b4-d2547124039f",
                  "input_descriptors": [
                    {
                      "id": "test_descriptor_id",
                      "name": "Test Descriptor Name",
                      "purpose": null,
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
                      "constraints": {
                        "id": null,
                        "name": null,
                        "purpose": null,
                        "format": null,
                        "fields": [
                          {
                            "path": [
                              "$.vct"
                            ],
                            "id": null,
                            "name": null,
                            "purpose": null,
                            "filter": {
                              "type": "string",
                              "const": "defaultTestVCT"
                            }
                          },
                          {
                            "path": [
                              "$.last_name"
                            ],
                            "id": null,
                            "name": null,
                            "purpose": null,
                            "filter": null
                          },
                          {
                            "path": [
                              "$.birthdate"
                            ],
                            "id": null,
                            "name": null,
                            "purpose": null,
                            "filter": null
                          }
                        ]
                      }
                    }
                  ]
                }
                """;
    }

    /**
     * Checks whether the JSON serialization and deserialization within the {@link Management} works as expected.
     */
    @Test
    void jsonReadWriteTest() throws JsonProcessingException {
        // GIVEN
        var entityBeforeSave = Management.builder()
                .id(UUID.randomUUID())
                .jwtSecuredAuthorizationRequest(true)
                .requestedPresentation(presentationDefinition())
                .walletResponse(walletResponse())
                .requestNonce("HelloNonce")
                .build();

        // WHEN
        managementEntityRepository.save(entityBeforeSave);
        var entityAfterSave = managementEntityRepository.findById(entityBeforeSave.getId()).orElseThrow();

        // THEN
        assertThat(entityAfterSave.getRequestedPresentation()).isEqualTo(entityBeforeSave.getRequestedPresentation());
        assertThat(entityAfterSave.getWalletResponse()).isEqualTo(entityBeforeSave.getWalletResponse());
        assertThat(entityAfterSave.getRequestedPresentation().inputDescriptors().getFirst().format().get("vc+sd-jwt").alg()).contains("ES256");
    }

    private ResponseData walletResponse() throws JsonProcessingException {
        return objectMapper.readValue(walletResponseJson(), ResponseData.class);
    }

    private PresentationDefinition presentationDefinition() throws JsonProcessingException {
        return objectMapper.readValue(presentationDefinitionJson(), PresentationDefinition.class);
    }

    private String walletResponseJson() {
        return """
                {
                  "errorCode": "client_rejected",
                  "errorDescription": "bla",
                  "credentialSubjectData": null
                }
                """;
    }
}