package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.service.management.fixtures.ApiFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.List;

import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RedirectURIUseCaseIT extends BaseVerificationControllerTest {

    @Autowired
    private MockMvc mock;
    @Autowired
    private ApplicationProperties applicationProperties;

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
        sendVerificationResponse(verificationUrl, vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri", startsWith("https://this.is.a.redirect.uri?session_nonce=test&response_code=")));
    }

    private CreateVerificationManagementDto createVerificationManagement(ResponseModeTypeDto responseModeTypeDto) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:webvh:scid:some-issuer-id"))
                .jwtSecuredAuthorizationRequest(true)
                .responseMode(responseModeTypeDto)
                .dcqlQuery(ApiFixtures.getDcqlQueryDto())
                .redirectURI(URI.create("https://this.is.a.redirect.uri?session_nonce=test"))
                .build();
    }
}
