package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.dto.VPApiVersion;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.service.management.fixtures.ApiFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
public class RedirectURIUseCaseIT extends BaseVerificationControllerTest {

    @Autowired
    private MockMvc mock;
    @Autowired
    private ApplicationProperties applicationProperties;

    @MockitoBean
    private DidResolverFacade didResolverFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PUBLIC_KEY = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"oqBwmYd3RAHs-sFe_U7UFTXbkWmPAaqKTHCvsV8tvxU\",\"y\":\"np4PjpDKNfEDk9qwzZPqjAawiZ8sokVOozHR-Kt89T4\"}";

    @Test
    public void testOpenIdClientMetadata() throws Exception {
        var request = createVerificationManagement(ResponseModeTypeDto.DIRECT_POST);

        var mgmt = BaseVerificationControllerTest.createVerificationRequest(mock, request);


        SDJWTCredentialMock emulator = new SDJWTCredentialMock(null, "did:webvh:scid:some-issuer-id" + "#key-1");
        mockDidResolverResponse(emulator);

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, mgmt.requestNonce(), "decentralized_identifier:" + applicationProperties.getClientId());
        var state = getStateFromVerificationRequest(mgmt.verificationUrl(), mgmt.requestNonce(), ResponseModeType.DIRECT_POST);

        var vpTokens = Map.of("identity_credential_dcql", List.of(vpToken));
        var submissionData = objectMapper.writeValueAsString(vpTokens);
        mock.perform(post(mgmt.verificationUrl().split("http://localhost:8080")[1] + "/response-data")
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("state", state)
                        .formField("vp_token", submissionData))
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

    private void mockDidResolverResponse(SDJWTCredentialMock sdjwt) {
        try {
            // Parse the JSON Web Key string into a Nimbus JWK object to ensure correct type
            JWK nimbusJwk = JWK.parse(KeyFixtures.issuerPublicKeyAsJsonWebKey());
            when(didResolverFacade.resolveKey(sdjwt.getKidHeaderValue())).thenReturn(nimbusJwk);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String getStateFromVerificationRequest(String requestUri, String nonce, ResponseModeType expectedResponseMode) throws ParseException, UnsupportedEncodingException, JOSEException {
        MvcResult requestObjectResult = assertDoesNotThrow(() -> (mock.perform(get(requestUri)
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andReturn()));

        var responseJwt = SignedJWT.parse(requestObjectResult.getResponse().getContentAsString());
        assertThat(responseJwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        assertThat(responseJwt.getHeader().getKeyID()).isEqualTo(applicationProperties.getSigningKeyVerificationMethod());
        assertThat(responseJwt.verify(new ECDSAVerifier(ECKey.parse(PUBLIC_KEY)))).isTrue();

        // checking claims
        var claims = responseJwt.getJWTClaimsSet();
        assertThat(claims.getStringClaim("response_type")).isEqualTo("vp_token");
        assertThat(claims.getStringClaim("response_mode")).isEqualTo(expectedResponseMode.toString());
        assertThat(claims.getStringClaim("nonce")).isEqualTo(nonce);
        assertThat(claims.getStringClaim("state"))
                .as("The verifier should provide a state").isNotBlank();
        return claims.getStringClaim("state");
    }
}
