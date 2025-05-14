/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

import java.text.ParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class RequestObjectServiceTest {
    private static final UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    RequestObjectService requestObjectService;

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
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
}
