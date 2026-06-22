package ch.admin.bj.swiyu.verifier.domain.statuslist;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import ch.admin.eid.did_sidekicks.DidDoc;
import com.nimbusds.jose.JOSEException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenStatusListReferenceTest {
    private final String issuerOfReferencedToken = "TEST_ISSUER_ID";
    @Mock
    private StatusListResolverAdapter statusListResolverAdapter;
    @Mock
    private DidJwtValidator credentialDidJwtValidator;
    @Mock
    private DidResolverFacade didResolverFacade;
    @Mock
    private DidDoc didDoc;
    private SDJWTCredentialMock emulator;

    @BeforeEach
    void beforeEach() throws JOSEException {
        emulator = new SDJWTCredentialMock();
        String tokenStatusList = createTokenStatusListTokenVerifiableCredential(
                StatusListGenerator.SPEC_STATUS_LIST,
                emulator.getKey(),
                emulator.getIssuerId(),
                emulator.getKidHeaderValue()
        );
        when(statusListResolverAdapter.resolveStatusList(SPEC_SUBJECT)).thenReturn(tokenStatusList);
    }

    private void stubDidJwtValidatorSuccess() throws Exception {
        when(credentialDidJwtValidator.getDidString(anyString())).thenReturn(issuerOfReferencedToken);
        when(didResolverFacade.resolveDid(issuerOfReferencedToken)).thenReturn(didDoc);
    }

    @Test
    void givenMatchingTokenStatusReference_whenVerified_thenSuccess() throws Exception {
        stubDidJwtValidatorSuccess();
        var tokenStatusListReference = new TokenStatusListReference(
                statusListResolverAdapter,
                Map.of("idx", 4, "uri", SPEC_SUBJECT),
                credentialDidJwtValidator,
                didResolverFacade,
                issuerOfReferencedToken,
                204800);
        tokenStatusListReference.verifyStatus();
    }

    @Test
    void givenInvalidTokenStatusListInvalidClaimBits_whenVerified_thenFails() throws Exception {
        when(statusListResolverAdapter.resolveStatusList(SPEC_SUBJECT)).thenReturn(
                createInvalidTokenStatusListTokenVerifiableCredentialInvalidClaimBits(
                        emulator.getKey(),
                        emulator.getIssuerId(),
                        emulator.getKidHeaderValue()
                )
        );
        stubDidJwtValidatorSuccess();

        var tokenStatusListReference = new TokenStatusListReference(
                statusListResolverAdapter,
                Map.of("idx", 4, "uri", SPEC_SUBJECT),
                credentialDidJwtValidator,
                didResolverFacade,
                issuerOfReferencedToken,
                204800);

        var err = assertThrows(VerificationException.class, tokenStatusListReference::verifyStatus);
        assertEquals(VerificationErrorResponseCode.INVALID_TOKEN_STATUS_LIST, err.getErrorResponseCode());
        Assertions.assertThat(err.getErrorDescription()).contains("Invalid REQUIRED claim 'bits'");
    }

    @Test
    void givenInvalidTokenStatusMissingClaimLst_whenVerified_thenFails() throws Exception {
        when(statusListResolverAdapter.resolveStatusList(SPEC_SUBJECT)).thenReturn(
                createInvalidTokenStatusListTokenVerifiableCredentialMissingClaimLst(
                        emulator.getKey(),
                        emulator.getIssuerId(),
                        emulator.getKidHeaderValue()
                )
        );
        stubDidJwtValidatorSuccess();

        var tokenStatusListReference = new TokenStatusListReference(
                statusListResolverAdapter,
                Map.of("idx", 4, "uri", SPEC_SUBJECT),
                credentialDidJwtValidator,
                didResolverFacade,
                issuerOfReferencedToken,
                204800);

        var exc = assertThrows(VerificationException.class, tokenStatusListReference::verifyStatus);
        assertEquals(VerificationErrorResponseCode.INVALID_TOKEN_STATUS_LIST, exc.getErrorResponseCode());
        Assertions.assertThat(exc.getErrorDescription()).contains("Missing REQUIRED claim 'lst'");
    }

    @Test
    void givenRevokedTokenStatusReference_whenVerified_thenThrowsCredentialRevokedException() throws Exception {
        stubDidJwtValidatorSuccess();
        // idx=0 is the position in the status list; the value at position 0 is 1 (revoked), according to SPEC_STATUS_LIST
        var tokenStatusListReference = new TokenStatusListReference(
                statusListResolverAdapter,
                Map.of("idx", 0, "uri", SPEC_SUBJECT),
                credentialDidJwtValidator,
                didResolverFacade,
                issuerOfReferencedToken,
                204800);

        var exc = assertThrows(VerificationException.class, tokenStatusListReference::verifyStatus);
        assertEquals(VerificationErrorResponseCode.CREDENTIAL_REVOKED, exc.getErrorResponseCode());
    }

    @Test
    void givenDifferentTokenStatusReference_whenVerified_thenFails() {
        var tokenStatusListReference = new TokenStatusListReference(
                statusListResolverAdapter,
                Map.of("idx", 4, "uri", SPEC_SUBJECT),
                credentialDidJwtValidator,
                didResolverFacade,
                "DIFFERENT-ISSUER",
                204800);
        var exc = assertThrows(VerificationException.class, tokenStatusListReference::verifyStatus);
        assertEquals(VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST, exc.getErrorResponseCode());
        Assertions.assertThat(exc.getErrorDescription()).contains("Failed to verify JWT: Issuer mismatch");
    }
}