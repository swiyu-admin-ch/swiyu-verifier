package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.VerificationManagementSeeder.FIXTURES;
import static ch.admin.bj.swiyu.verifier.common.profile.SwissProfileVersions.PROFILE_VERSION_PARAM;
import static ch.admin.bj.swiyu.verifier.common.profile.SwissProfileVersions.VERIFICATION_PROFILE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the signed request object returned by the request-object endpoint, its JOSE header, claims and error cases.
 */
public class VerificationRequestObjectIT extends AbstractVerificationControllerIT {

    /**
     * Verifies that a secured verification yields a signed request object with the expected header, claims and DCQL query.
     */
    @Test
    void givenSecuredVerification_whenGetRequestObject_thenCompleteSignedRequestObject() throws Exception {
        var expectedClientIdWithPrefix = applicationProperties.getClientIdPrefix() + ":" + applicationProperties.getClientId();
        mockMvc.perform(get(String.format("/oid4vp/api/request-object/%s", FIXTURES.secured()))
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    var responseJwt = SignedJWT.parse(result.getResponse().getContentAsString());
                    assertThat(responseJwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
                    assertThat(responseJwt.getHeader().getKeyID()).isEqualTo(applicationProperties.getSigningKeyVerificationMethod());
                    assertThat(responseJwt.getHeader().getCustomParam(PROFILE_VERSION_PARAM))
                            .as("Signed Request Object JOSE header must identify the Swiss verification profile")
                            .isEqualTo(VERIFICATION_PROFILE_VERSION);
                    assertThat(responseJwt.verify(new ECDSAVerifier(ECKey.parse(PUBLIC_KEY)))).isTrue();

                    // checking claims
                    var claims = responseJwt.getJWTClaimsSet();
                    assertThat(claims.getStringClaim("client_id")).isEqualTo(expectedClientIdWithPrefix);
                    assertThat(claims.getStringClaim("response_type")).isEqualTo("vp_token");
                    assertThat(claims.getStringClaim("response_mode")).isEqualTo("direct_post");
                    assertThat(claims.getStringClaim("nonce")).isNotNull();
                    assertThat(claims.getStringClaim("response_uri")).isEqualTo(String.format("%s/oid4vp/api/request-object/%s/response-data", applicationProperties.getExternalUrl(), FIXTURES.secured()));

                    assertDcqlIsComplete(claims);

                    assertThat(result.getResponse().getContentAsString()).doesNotContain("null");
                });
    }

    /**
     * Verifies that the client_id claim omits the prefix when no client id prefix is configured.
     */
    @Test
    void givenNoClientIdPrefix_whenGetRequestObject_thenClientIdWithoutPrefix() throws Exception {
        when(applicationProperties.getClientIdPrefix()).thenReturn(null);
        mockMvc.perform(get(String.format("/oid4vp/api/request-object/%s", FIXTURES.secured()))
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    var responseJwt = SignedJWT.parse(result.getResponse().getContentAsString());
                    var claims = responseJwt.getJWTClaimsSet();
                    assertThat(claims.getStringClaim("client_id")).isEqualTo(applicationProperties.getClientId());
                });
    }

    /**
     * Verifies that an unknown request object id yields HTTP 404.
     */
    @Test
    void givenUnknownRequestId_whenGetRequestObject_thenNotFound() throws Exception {
        UUID notExistingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        mockMvc.perform(get(String.format("/request-object/%s", notExistingRequestId)))
                .andExpect(status().isNotFound());
    }

    /**
     * Asserts that the DCQL query requests at least one complete credential and defines no credential sets.
     */
    private void assertDcqlIsComplete(JWTClaimsSet claims) {
        var dcqlQuery = (LinkedTreeMap) claims.getClaim("dcql_query");
        assertThat(dcqlQuery).isNotNull().isNotEmpty();
        var dcqlRequestedCredentials = (List<?>) dcqlQuery.get("credentials");
        assertThat(dcqlRequestedCredentials).isNotNull().isNotEmpty();
        var firstRequestedCredential = (Map<?, ?>) dcqlRequestedCredentials.getFirst();
        assertThat(firstRequestedCredential.get("id")).isNotNull().asString().isNotBlank();
        assertThat(firstRequestedCredential.get("format")).isNotNull().asString().isNotBlank();
        assertThat(firstRequestedCredential.get("meta")).isNotNull();
        assertThat(firstRequestedCredential.get("claims")).isNotNull();

        assertThat(dcqlQuery.get("credential_sets")).isNull();
    }
}
