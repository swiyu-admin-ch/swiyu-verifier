package ch.admin.bj.swiyu.verifier.service.oid4vp.domain.publickey;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader.TRUST_STATEMENT_ISSUANCE_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
/**
 * Unit tests for {@link IssuerPublicKeyLoader}.
 * <p>
 * After migration to {@code swiyu-sdjwt-validator}, public-key loading is no longer handled here.
 * Only trust-statement retrieval is tested.
 * </p>
 */
class IssuerPublicKeyLoaderTest {
    private IssuerPublicKeyLoader publicKeyLoader;
    private DidResolverFacade mockedDidResolverFacade;
    @BeforeEach
    void setUp() {
        mockedDidResolverFacade = mock(DidResolverFacade.class);
        publicKeyLoader = new IssuerPublicKeyLoader(mockedDidResolverFacade, new ObjectMapper());
    }
    @Test
    void loadTrustStatement_parsesListFromJson() throws JsonProcessingException {
        String trustRegistryUri = "https://registry.example";
        String vct = "vct-1";
        List<String> expectedStatements = List.of("jwt-one", "jwt-two");
        String expectedUri = trustRegistryUri + TRUST_STATEMENT_ISSUANCE_ENDPOINT;
        when(mockedDidResolverFacade.resolveTrustStatement(expectedUri, vct))
                .thenReturn("[\"%s\", \"%s\"]".formatted(expectedStatements.getFirst(), expectedStatements.get(1)));
        var statements = publicKeyLoader.loadTrustStatement(trustRegistryUri, vct);
        assertThat(statements).isNotNull();
        assertEquals(2, statements.size());
        assertEquals(expectedStatements.getFirst(), statements.getFirst());
        assertEquals(expectedStatements.get(1), statements.get(1));
    }
}
