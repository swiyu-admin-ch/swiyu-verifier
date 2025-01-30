package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntity;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import org.checkerframework.checker.signedness.qual.Signed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.text.ParseException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class RequestObjectServiceTest {
    private static final UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");

    @Autowired
    RequestObjectService requestObjectService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void requestObjectTest() throws ParseException {
        var requestObject = requestObjectService.assembleRequestObject(requestId);

        assertThat(requestObject).isNotNull();
        if (requestObject instanceof String) {
           SignedJWT jwt = SignedJWT.parse((String) requestObject);
           assertThat(jwt).isNotNull();
           assertThat(jwt.getJWTClaimsSet()).isNotNull();
        }

        Map<String, Object> requestObjectProperties = objectMapper.convertValue(requestObject, Map.class);


        assertThat(requestObjectProperties.get("version")).isEqualTo("1.0.0");
    }
}
