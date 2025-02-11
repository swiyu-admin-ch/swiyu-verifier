/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase
@AutoConfigureJson
class ManagementEntityRepositoryIT {

    @Autowired
    private ManagementEntityRepository managementEntityRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Checks whether the JSON serialization and deserialization within the {@link ManagementEntity} works as expected.
     */
    @Test
    void jsonReadWriteTest() throws JsonProcessingException {
        // GIVEN
        var entityBeforeSave = ManagementEntity.builder()
                .id(UUID.randomUUID())
                .jwtSecuredAuthorizationRequest(true)
                .requestedPresentation(presentationDefinition())
                .walletResponse(walletResponse())
                .requestNonce("HelloNonce")
                .build();

        // WHEN
        managementEntityRepository.saveAndFlush(entityBeforeSave);
        commit();
        var entityAfterSave = managementEntityRepository.findById(entityBeforeSave.getId()).orElseThrow();

        // THEN
        assertThat(entityAfterSave.getRequestedPresentation()).isEqualTo(entityBeforeSave.getRequestedPresentation());
        assertThat(entityAfterSave.getWalletResponse()).isEqualTo(entityBeforeSave.getWalletResponse());
        assertThat(entityAfterSave.getRequestedPresentation().inputDescriptors().get(0).format().get("vc+sd-jwt").alg()).contains("ES256");

    }

    private ResponseData walletResponse() throws JsonProcessingException {
        return objectMapper.readValue(walletResponseJson(), ResponseData.class);
    }

    private PresentationDefinition presentationDefinition() throws JsonProcessingException {
        return objectMapper.readValue(presentationDefinitionJson(), PresentationDefinition.class);
    }

    private static void commit() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
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

}
