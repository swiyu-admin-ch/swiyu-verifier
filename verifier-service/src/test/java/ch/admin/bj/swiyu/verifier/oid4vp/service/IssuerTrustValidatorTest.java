package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.oid4vp.IssuerTrustValidator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.SdJwtVpTokenVerifier;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.ISSUER_NOT_ACCEPTED;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void validateTrust_allowsIssuerWhenTrustedViaTrustAnchorAndTrustStatement() {
        String vct = "vct:test";
        String issuerDid = "did:example:issuer-2";
        TrustAnchor anchor = new TrustAnchor(issuerDid, "https://registry.example/");
        List<String> didTrustAnchors = List.of(anchor.did());

        Management management = Management.builder()
                .acceptedIssuerDids(didTrustAnchors)
                .trustAnchors(List.of(anchor))
                .build();

        assertThatCode(() -> issuerTrustValidator.validateTrust(issuerDid, vct, management))
                .doesNotThrowAnyException();
    }
}
