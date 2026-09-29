package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.HSMProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import static ch.admin.bj.swiyu.verifier.common.profile.SwissProfileVersions.PROFILE_VERSION_PARAM;
import static ch.admin.bj.swiyu.verifier.common.profile.SwissProfileVersions.VERIFICATION_PROFILE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtSigningServiceTest {

    private static final String VERIFICATION_METHOD = "did:example:verifier#key-1";

    @Test
    void signJwt_addsSwissProfileVersionToProtectedHeader() throws Exception {
        // Given
        final ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.setHsm(new HSMProperties());

        final ECKey signingKey = new ECKeyGenerator(Curve.P_256).generate();
        final JwsSignatureFacade jwsSignatureFacade = mock(JwsSignatureFacade.class);
        when(jwsSignatureFacade.createSigner(any(SignatureConfiguration.class), isNull(), isNull()))
                .thenReturn(new ECDSASigner(signingKey));

        final JwtSigningService jwtSigningService = new JwtSigningService(
                applicationProperties,
                jwsSignatureFacade
        );
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-subject")
                .build();

        // When
        final var signedJwt = jwtSigningService.signJwt(claims, null, null, VERIFICATION_METHOD);

        // Then
        assertThat(signedJwt.getHeader().getCustomParam(PROFILE_VERSION_PARAM))
                .as("signed Request Object JOSE header profile_version")
                .isEqualTo(VERIFICATION_PROFILE_VERSION);
        assertThat(signedJwt.getJWTClaimsSet().getClaim(PROFILE_VERSION_PARAM))
                .as("profile_version must not be duplicated in the Request Object payload")
                .isNull();
        assertThat(signedJwt.verify(new ECDSAVerifier(signingKey.toPublicJWK())))
                .as("Request Object signature")
                .isTrue();
    }
}
