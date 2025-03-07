package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.StatusListGenerator.createTokenStatusListTokenVerifiableCredential;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TokenStatusListReferenceTest {
    @Mock
    private StatusListResolverAdapter statusListResolverAdapter;
    @Mock
    private IssuerPublicKeyLoader issuerPublicKeyLoader;

    private final String issuerOfReferencedToken = "TEST_ISSUER_ID";

    @BeforeEach
    public void beforeEac() throws JOSEException, LoadingPublicKeyOfIssuerFailedException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        String tokenStatusList = createTokenStatusListTokenVerifiableCredential(
                StatusListGenerator.SPEC_STATUS_LIST,
                emulator.getKey(),
                emulator.getIssuerId(),
                emulator.getKidHeaderValue()
        );
        when(issuerPublicKeyLoader.loadPublicKey(issuerOfReferencedToken, "TEST_ISSUER_ID#key-1")).thenReturn(
                emulator.getKey().toECPublicKey()
        );
        when(statusListResolverAdapter.resolveStatusList(eq("https://example.com/statuslists/1"))).thenReturn(tokenStatusList);
    }


    @Test
    public void givenMatchingTokenStatusReference_whenVerified_thenSuccess() {
        var tokenStatusListReference = new TokenStatusListReference(
                statusListResolverAdapter,
                Map.of(
                        "idx", 4,
                        "uri", "https://example.com/statuslists/1"
                ),
                issuerPublicKeyLoader,
                issuerOfReferencedToken
        );
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
        );
        var err = assertThrows(VerificationException.class, tokenStatusListReference::verifyStatus);
        assertEquals(err.getErrorDescription(), "Failed to verify JWT: Issuer of the status list does not match the issuer of the referenced token");
    }
}
