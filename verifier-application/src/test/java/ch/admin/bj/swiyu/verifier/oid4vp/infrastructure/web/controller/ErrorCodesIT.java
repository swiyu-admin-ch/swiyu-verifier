package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.api.VerificationClientErrorDto;
import ch.admin.bj.swiyu.verifier.api.VerificationErrorResponseCodeDto;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
class ErrorCodesIT extends BaseVerificationControllerTest {

    private static final String BASE_URL = "/management/api/verifications";
    private final String responseDataUriFormat = "/oid4vp/api/request-object/%s/response-data";

    @Autowired
    private MockMvc mock;
    @MockitoBean
    private DidResolverAdapter didResolverAdapter;


    @Test
    void testExistingErrorCodesFromClientToManagement_thenSuccess() throws Exception {
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("error", "access_denied"))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @ParameterizedTest
    @EnumSource(VerificationClientErrorDto.class)
    void testExistingErrorCodesFromClientToManagement_thenSuccess(VerificationClientErrorDto verificationClientErrorCode) throws Exception {
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("error", verificationClientErrorCode.toString()))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);

        mock.perform(get(BASE_URL + "/" + REQUEST_ID_SECURED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(VerificationStatus.FAILED.toString()))
                .andExpect(jsonPath("$.wallet_response.error_code").value(VerificationErrorResponseCodeDto.CLIENT_REJECTED.toString()));
    }
}