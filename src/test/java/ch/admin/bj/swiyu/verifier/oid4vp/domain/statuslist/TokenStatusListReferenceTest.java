package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import java.util.Map;

import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.StatusListGenerator.createTokenStatusListTokenVerifiableCredential;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.oid4vp.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bj.swiyu.verifier.oid4vp.service.statuslist.StatusListResolverAdapter;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TokenStatusListReferenceTest {
    private final String issuerOfReferencedToken = "TEST_ISSUER_ID";
    @Mock
    private StatusListResolverAdapter statusListResolverAdapter;
    @Mock
    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    private SDJWTCredentialMock emulator;

    @BeforeEach
    public void beforeEac() throws JOSEException, LoadingPublicKeyOfIssuerFailedException {
        emulator = new SDJWTCredentialMock();
        String tokenStatusList = createTokenStatusListTokenVerifiableCredential(
                StatusListGenerator.SPEC_STATUS_LIST,
                emulator.getKey(),
                emulator.getIssuerId(),
                emulator.getKidHeaderValue()
        );
        when(statusListResolverAdapter.resolveStatusList(eq("https://example.com/statuslists/1"))).thenReturn(tokenStatusList);
    }


    @Test
    public void givenMatchingTokenStatusReference_whenVerified_thenSuccess() throws LoadingPublicKeyOfIssuerFailedException, JOSEException {
        when(issuerPublicKeyLoader.loadPublicKey(issuerOfReferencedToken, "TEST_ISSUER_ID#key-1")).thenReturn(
                emulator.getKey().toECPublicKey()
        );
        var tokenStatusListReference = new TokenStatusListReference(
                statusListResolverAdapter,
                Map.of(
                        "idx", 4,
                        "uri", "https://example.com/statuslists/1"
                ),
                issuerPublicKeyLoader,
                issuerOfReferencedToken
                , 204800);
        tokenStatusListReference.verifyStatus();
    }

    @Test
    public void givenDifferentTokenStatusReference_whenVerified_thenFails() {
        var tokenStatusListReference = new TokenStatusListReference(
                statusListResolverAdapter,
                Map.of(
                        "idx", 4,
                        "uri", "https://example.com/statuslists/1"
                ),
                issuerPublicKeyLoader,
                "DIFFERENT-ISSUER"
                , 204800);
        var err = assertThrows(VerificationException.class, tokenStatusListReference::verifyStatus);
        assertTrue(err.getErrorDescription().contains("Failed to verify JWT: Invalid JWT token. JWT iss claim has value TEST_ISSUER_ID, must be DIFFERENT-ISSUER"));
    }
}