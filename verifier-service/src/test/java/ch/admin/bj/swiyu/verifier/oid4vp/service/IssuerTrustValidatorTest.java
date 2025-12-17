package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.oid4vp.IssuerTrustValidator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.SdJwtVpTokenVerifier;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.ISSUER_NOT_ACCEPTED;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerTrustValidatorTest {

    @Mock
    IssuerPublicKeyLoader issuerPublicKeyLoader;

    @Mock
    SdJwtVpTokenVerifier sdJwtVpTokenVerifier;

    @InjectMocks
    IssuerTrustValidator issuerTrustValidator;

    @Test
    void validateTrust_allowsIssuerWhenInAcceptedIssuerDids() {
        Management management = Management.builder()
                .acceptedIssuerDids(List.of("did:example:issuer-1"))
                .build();

        assertThatCode(() -> issuerTrustValidator.validateTrust("did:example:issuer-1", "vct:test", management))
                .doesNotThrowAnyException();
    }

    @Test
    void validateTrust_throwsWhenIssuerNotAcceptedAndNoTrustAnchors() {
        TrustAnchor anchor = new TrustAnchor("did:example:anchor", "https://registry.example/");
        Management management = Management.builder()
                .acceptedIssuerDids(List.of())
                .trustAnchors(List.of(anchor))
                .build();

        assertThatThrownBy(() -> issuerTrustValidator.validateTrust("did:example:unknown", "vct:test", management))
                .isInstanceOf(VerificationException.class)
                .matches(ex -> ((VerificationException) ex).getErrorResponseCode() == ISSUER_NOT_ACCEPTED);
    }

    @Test
    void validateTrust_allowsIssuerWhenTrustedViaTrustAnchorAndTrustStatement() throws Exception {
        TrustAnchor anchor = new TrustAnchor("did:example:anchor", "https://registry.example/");
        Management management = Management.builder()
                .acceptedIssuerDids(List.of())
                .trustAnchors(List.of(anchor))
                .build();

        String issuerDid = "did:example:issuer-2";
        String vct = "vct:test";

        String rawTrustStatement = "raw-trust-statement";
        when(issuerPublicKeyLoader.loadTrustStatement(anchor.trustRegistryUri(), vct))
                .thenReturn(List.of(rawTrustStatement));

        // Stub so that verifyVpTokenTrustStatement returns a SdJwt with verified claims
        when(sdJwtVpTokenVerifier.verifyVpTokenTrustStatement(any(SdJwt.class), any(Management.class)))
                .thenAnswer(invocation -> {
                    SdJwt sdJwtArg = invocation.getArgument(0);
                    JWTClaimsSet claims = new JWTClaimsSet.Builder()
                            .subject(issuerDid)
                            .issuer(anchor.did())
                            .claim("canIssue", vct)
                            .build();
                    sdJwtArg.setClaims(claims);
                    return sdJwtArg;
                });

        assertThatCode(() -> issuerTrustValidator.validateTrust(issuerDid, vct, management))
                .doesNotThrowAnyException();
    }
}
