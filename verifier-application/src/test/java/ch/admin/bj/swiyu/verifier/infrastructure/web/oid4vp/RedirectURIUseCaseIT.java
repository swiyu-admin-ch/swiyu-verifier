package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationResponseDto;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.service.management.fixtures.ApiFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class RedirectURIUseCaseIT extends BaseVerificationControllerTest {

    @Autowired
    private MockMvc mock;
    @Autowired
    private ApplicationProperties applicationProperties;

    private final String sessionNonce = "sessionNonce";
    private final String redirectURI = "https://this.is.a.redirect.uri";

    @ParameterizedTest
    @EnumSource(ResponseModeTypeDto.class)
    public void givenResponseMode_whenVerificationCompletes_thenRedirectUriReturned(ResponseModeTypeDto responseModeTypeDto) throws Exception {
        var request = createVerificationManagement(responseModeTypeDto);
        var mgmt = BaseVerificationControllerTest.createVerificationRequest(mock, request);
        var requestObject = getRequestObject(mgmt.verificationUrl());

        SDJWTCredentialMock emulator = new SDJWTCredentialMock(null, "did:webvh:scid:some-issuer-id" + "#key-1");
        mockDidResolverResponse(emulator);

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, mgmt.requestNonce(), "decentralized_identifier:" + applicationProperties.getClientId());

        var verificationUrl = mgmt.verificationUrl().split(applicationProperties.getExternalUrl())[1] + "/response-data";
        var response = sendVerificationResponse(verificationUrl, vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri", startsWith(redirectURI)))
                .andReturn()
                .getResponse();

        VerificationPresentationResponseDto verificationResponse = objectMapper.readValue(response.getContentAsString(), VerificationPresentationResponseDto.class);

        var queryParams = UriComponentsBuilder.fromUri(verificationResponse.redirectURI())
                .build()
                .getQueryParams();

        assertThat(queryParams.get("session_nonce")).isEqualTo(List.of(sessionNonce));
        assertThat(queryParams.get("response_code")).isNotNull().isNotEmpty();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("/management/api/verifications").pathSegment(mgmt.id().toString());

        // cannot be fetched without response code (classic way)
        mockMvc.perform(get(builder.toUriString()))
                .andExpect(status().isBadRequest());

        // should work with response_code
        mockMvc.perform(get(builder.queryParam("response_code", queryParams.get("response_code").getFirst()).toUriString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCESS"));

    }

    private CreateVerificationManagementDto createVerificationManagement(ResponseModeTypeDto responseModeTypeDto) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:webvh:scid:some-issuer-id"))
                .jwtSecuredAuthorizationRequest(true)
                .responseMode(responseModeTypeDto)
                .dcqlQuery(ApiFixtures.getDcqlQueryDto())
                .redirectURI(URI.create(redirectURI + "?session_nonce=" + sessionNonce))
                .build();
    }
}
