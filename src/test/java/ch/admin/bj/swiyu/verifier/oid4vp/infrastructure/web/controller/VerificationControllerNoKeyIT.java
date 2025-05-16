/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-no-key")
@AutoConfigureMockMvc
class VerificationControllerNoKeyIT {

    private static final UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");

    @Autowired
    private MockMvc mock;

    @Autowired
    private ApplicationProperties applicationProperties;




    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldGetSignedRequestObject_thenFailDuetoNoKey() throws Exception {

        mock.perform(get(String.format("/api/v1/request-object/%s", requestId))).andExpect(status().is5xxServerError());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt_no_signature.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldGetRequestObject() throws Exception {
        mock.perform(get(String.format("/api/v1/request-object/%s", requestId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("client_id").value(applicationProperties.getClientId()))
                .andExpect(jsonPath("client_id_scheme").value(applicationProperties.getClientIdScheme()))
                .andExpect(jsonPath("response_type").value("vp_token"))
                .andExpect(jsonPath("response_mode").value("direct_post"))
                .andExpect(jsonPath("nonce").exists());
    }

    @Test
    void testDeserializationOfPresentationDefinition() throws Exception {
        String json = "{\"id\": \"cf244758-00f9-4fa0-83ff-6719bac358a2\", \"name\": \"Presentation Definition Name\", \"purpose\": \"Presentation Definition Purpose\", \"input_descriptors\": [{\"id\": \"test_descriptor_id\", \"purpose\": \"Input Descriptor Purpose\", \"format\": {\"vc+sd-jwt\": {\"sd-jwt_alg_values\": [\"ES256\"], \"kb-jwt_alg_values\": [\"ES256\"]}}, \"name\": \"Test Descriptor Name\", \"constraints\": {\"fields\": [{\"path\": [\"$\"]}]}}]}";

        ObjectMapper objectMapper = new ObjectMapper();
        PresentationDefinition presentationDefinition = objectMapper.readValue(json, PresentationDefinition.class);

        assertNotNull(presentationDefinition, "Deserialization should produce a non-null object.");
//        assertNotNull(presentationDefinition.getId(), "ID should not be null.");
//        assertNotNull(presentationDefinition.getInputDescriptors(), "Input descriptors should not be null.");
    }

}
