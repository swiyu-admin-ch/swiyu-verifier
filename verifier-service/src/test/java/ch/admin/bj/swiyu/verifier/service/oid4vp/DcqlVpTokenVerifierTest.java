package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDJWT;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.getClaimsFromSdJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DcqlVpTokenVerifierTest {

    @Mock
    private SdJwtVpTokenVerifier sdJwtVpTokenVerifier;

    @Mock
    private IssuerTrustValidator issuerTrustValidator;

    @InjectMocks
    private DcqlVpTokenVerifier dcqlVpTokenVerifier;

    private SdJwt vpToken;
    private Management management;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Initialize mocks
        vpToken = mock(SdJwt.class);
        management = mock(Management.class);
    }

    @Test
    void verifyVpTokenForDCQLRequest_whenVpTokenIsInvalid_thenThrowsException() {
        doThrow(new RuntimeException("Invalid JWT")).when(sdJwtVpTokenVerifier).verifyVerifiableCredentialJWT(any(), any());
        assertThatThrownBy(() -> dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(vpToken, management, DcqlCredential.builder().build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid JWT");
    }

    /**
     * Expecting a holder binding but receiving none should yield an exception
     * @param explicitlySet if the cryptogrphic holder binding is explicitly requested
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void verifyVpTokenForDCQLRequest_whenKeyBindingIsMissingAndRequired_thenThrowsException(boolean explicitlySet) {
        when(vpToken.getClaims()).thenReturn(new JWTClaimsSet.Builder().issuer("Test").build());
        when(vpToken.hasKeyBinding()).thenReturn(false);
        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(explicitlySet ? Boolean.TRUE : null).build();

        assertThatThrownBy(() -> dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(vpToken, management, dcqlCredential))
                .isInstanceOf(VerificationException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void verifyVpTokenForDCQLRequest_whenAllValidationsPass_thenReturnsVpToken(boolean explicitlySet) {
        when(vpToken.getClaims()).thenReturn(new JWTClaimsSet.Builder().issuer("Test").build());
        when(vpToken.hasKeyBinding()).thenReturn(true);
        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(explicitlySet ? Boolean.TRUE : null).build();

        SdJwt verifiedToken = dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(vpToken, management, dcqlCredential);

        assertThat(verifiedToken).isEqualTo(vpToken);
    }

    @Test
    void verifyVpTokenForDCQLRequest_deeplyNested_thenReturnsVpToken() throws ParseException, JOSEException {

        List<Disclosure> disclosures = new ArrayList<>();
        var claimsForSdJWT = getClaimsFromSdJwt(disclosures);

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(claimsForSdJWT.build());

        when(vpToken.getClaims()).thenReturn(claimsSet);
        when(vpToken.hasKeyBinding()).thenReturn(true);


        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(Boolean.TRUE).build();

        SdJwt verifiedToken = assertDoesNotThrow(() -> dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(vpToken, management, dcqlCredential));

        assertThat(verifiedToken).isEqualTo(vpToken);
    }
}