package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.oid4vp.IssuerTrustValidator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.SdJwtVpTokenVerifier;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.UNSUPPORTED_FORMAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerTrustValidatorTest {

    @Mock
    DidResolverFacade issuerPublicKeyLoader;

    @Mock
    SdJwtVpTokenVerifier sdJwtVpTokenVerifier;

    @Mock
    TrustRegistryProperties trustRegistryProperties;

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
    void validateTrust_throwsWhenIssuerNotAccepted() {
        when(trustRegistryProperties.getTrustIssuerDid()).thenReturn("did:webvh:scid:trust-issuer");
        Management management = Management.builder()
                .acceptedIssuerDids(List.of())
                .build();

        var ex = assertThrows(VerificationException.class,
            () -> issuerTrustValidator.validateTrust("did:example:unknown", "vct:test", management));
        assertThat(ex.getErrorResponseCode()).as("The DID format is not known").isEqualTo(UNSUPPORTED_FORMAT);
    }
}
