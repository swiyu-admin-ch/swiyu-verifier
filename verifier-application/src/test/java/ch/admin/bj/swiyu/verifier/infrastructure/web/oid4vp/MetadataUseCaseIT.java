package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import com.nimbusds.jose.JWSAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MetadataUseCaseIT extends BaseVerificationControllerTest {

    @Test
    void shouldGetCorrectMandatoryMetadata_thenSuccess() {
        assertDoesNotThrow(() -> this.getMetadata()
                .andExpect(jsonPath("$.client_id").value(applicationProperties.getClientIdWithPrefix()))
                .andExpect(jsonPath("$.vp_formats_supported['dc+sd-jwt']['sd-jwt_alg_values']").isArray())
                .andExpect(jsonPath("$.vp_formats_supported['dc+sd-jwt']['sd-jwt_alg_values'][0]").value(JWSAlgorithm.ES256.getName()))
                .andExpect(jsonPath("$.vp_formats_supported['dc+sd-jwt']['sd-jwt_alg_values'][1]").value(JWSAlgorithm.Ed25519.getName()))
                .andExpect(jsonPath("$.jwks").doesNotExist()));
    }

    private ResultActions getMetadata() throws Exception {
        return mockMvc.perform(get("/oid4vp/api/openid-client-metadata.json")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk());
    }
}
