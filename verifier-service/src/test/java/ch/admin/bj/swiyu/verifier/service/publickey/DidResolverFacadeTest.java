package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;
import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.eid.did_sidekicks.DidDoc;
import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link DidResolverFacade}.
 */
@ExtendWith(MockitoExtension.class)
class DidResolverFacadeTest {

    @Mock
    private DidResolverAdapter didResolverAdapter;

    @Mock
    private UrlRewriteProperties urlRewriteProperties;

    @Mock
    private DidDoc didDoc;

    @Mock
    private JWK mockJwk;

    private DidResolverFacade didResolverFacade;

    private static final String TEST_KEY_ID = "did:example:123#key-1";
    private static final String TEST_DID = "did:example:123";
    private static final String TEST_TRUST_REGISTRY_URL = "https://trustregistry.example.com";
    private static final String TEST_VCT = "VerifiableCredential";
    private static final String TEST_TRUST_STATEMENT = "{\"trust\":\"statement\"}";

    @BeforeEach
    void setUp() {
        didResolverFacade = new DidResolverFacade(didResolverAdapter, urlRewriteProperties);
    }

    // --- Tests for resolveKey ---

    @Test
    void resolveKey_withValidKeyId_returnsJwk() throws Exception {
        // Given
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveKey(TEST_KEY_ID, urlMappings)).thenReturn(mockJwk);

        // When
        JWK result = didResolverFacade.resolveKey(TEST_KEY_ID);

        // Then
        assertThat(result).isEqualTo(mockJwk);
        verify(didResolverAdapter, times(1)).resolveKey(TEST_KEY_ID, urlMappings);
        verify(urlRewriteProperties, times(1)).getUrlMappings();
    }

    @Test
    void resolveKey_withDidTdwKeyId_delegatesToAdapter() throws Exception {
        // Given
        String didTdwKeyId = "did:tdw:example.com:abc123#key-1";
        Map<String, String> urlMappings = Map.of("example.com", "localhost:8080");
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveKey(didTdwKeyId, urlMappings)).thenReturn(mockJwk);

        // When
        JWK result = didResolverFacade.resolveKey(didTdwKeyId);

        // Then
        assertThat(result).isEqualTo(mockJwk);
        verify(didResolverAdapter).resolveKey(didTdwKeyId, urlMappings);
    }

    @Test
    void resolveKey_withDidWebvhKeyId_delegatesToAdapter() throws Exception {
        // Given
        String didWebvhKeyId = "did:webvh:example.com:abc123#key-1";
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveKey(didWebvhKeyId, urlMappings)).thenReturn(mockJwk);

        // When
        JWK result = didResolverFacade.resolveKey(didWebvhKeyId);

        // Then
        assertThat(result).isEqualTo(mockJwk);
        verify(didResolverAdapter).resolveKey(didWebvhKeyId, urlMappings);
    }

    @Test
    void resolveKey_whenAdapterThrowsException_propagatesException() {
        // Given
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveKey(TEST_KEY_ID, urlMappings))
                .thenThrow(new DidResolverException("Resolution failed"));

        // When / Then
        assertThatThrownBy(() -> didResolverFacade.resolveKey(TEST_KEY_ID))
                .isInstanceOf(DidResolverException.class)
                .hasMessage("Resolution failed");
    }

    // --- Tests for resolveDid ---

    @Test
    void resolveDid_withValidDid_returnsDidDoc() throws Exception {
        // Given
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveDid(TEST_DID, urlMappings)).thenReturn(didDoc);

        // When
        DidDoc result = didResolverFacade.resolveDid(TEST_DID);

        // Then
        assertThat(result).isEqualTo(didDoc);
        verify(didResolverAdapter, times(1)).resolveDid(TEST_DID, urlMappings);
        verify(urlRewriteProperties, times(1)).getUrlMappings();
    }

    @Test
    void resolveDid_withNullDid_throwsIllegalArgumentException() {
        // When / Then
        assertThatThrownBy(() -> didResolverFacade.resolveDid(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("did must not be null");

        // Verify adapter was never called
        verify(didResolverAdapter, never()).resolveDid(any(), any());
    }

    @Test
    void resolveDid_whenAdapterThrowsDidResolverException_propagatesException() {
        // Given
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveDid(TEST_DID, urlMappings))
                .thenThrow(new DidResolverException("DID resolution failed"));

        // When / Then
        assertThatThrownBy(() -> didResolverFacade.resolveDid(TEST_DID))
                .isInstanceOf(DidResolverException.class)
                .hasMessage("DID resolution failed");
    }

    @Test
    void resolveDid_withUrlMappings_passesThemToAdapter() throws Exception {
        // Given
        Map<String, String> urlMappings = Map.of("remote.com", "local.com");
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveDid(TEST_DID, urlMappings)).thenReturn(didDoc);

        // When
        DidDoc result = didResolverFacade.resolveDid(TEST_DID);

        // Then
        assertThat(result).isEqualTo(didDoc);
        verify(didResolverAdapter).resolveDid(TEST_DID, urlMappings);
    }

    // --- Tests for resolveTrustStatement ---

    @Test
    void resolveTrustStatement_withValidParameters_returnsTrustStatement() {
        // Given
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT, urlMappings))
                .thenReturn(TEST_TRUST_STATEMENT);

        // When
        String result = didResolverFacade.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT);

        // Then
        assertThat(result).isEqualTo(TEST_TRUST_STATEMENT);
        verify(didResolverAdapter, times(1))
                .resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT, urlMappings);
    }

    @Test
    void resolveTrustStatement_whenAdapterThrowsHttpStatusCodeException_returnsNull() {
        // Given
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT, urlMappings))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not found"));

        // When
        String result = didResolverFacade.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT);

        // Then
        assertThat(result).isNull();
        verify(didResolverAdapter, times(1))
                .resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT, urlMappings);
    }

    @Test
    void resolveTrustStatement_when404Error_returnsNull() {
        // Given
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT, urlMappings))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When
        String result = didResolverFacade.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void resolveTrustStatement_when500Error_returnsNull() {
        // Given
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT, urlMappings))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        String result = didResolverFacade.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void resolveTrustStatement_withUrlMappings_passesThemToAdapter() {
        // Given
        Map<String, String> urlMappings = Map.of("prod.example.com", "dev.example.com");
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT, urlMappings))
                .thenReturn(TEST_TRUST_STATEMENT);

        // When
        String result = didResolverFacade.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT);

        // Then
        assertThat(result).isEqualTo(TEST_TRUST_STATEMENT);
        verify(didResolverAdapter).resolveTrustStatement(TEST_TRUST_REGISTRY_URL, TEST_VCT, urlMappings);
    }

    @Test
    void resolveTrustStatement_withEmptyVct_delegatesToAdapter() {
        // Given
        String emptyVct = "";
        Map<String, String> urlMappings = Collections.emptyMap();
        when(urlRewriteProperties.getUrlMappings()).thenReturn(urlMappings);
        when(didResolverAdapter.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, emptyVct, urlMappings))
                .thenReturn(TEST_TRUST_STATEMENT);

        // When
        String result = didResolverFacade.resolveTrustStatement(TEST_TRUST_REGISTRY_URL, emptyVct);

        // Then
        assertThat(result).isEqualTo(TEST_TRUST_STATEMENT);
        verify(didResolverAdapter).resolveTrustStatement(TEST_TRUST_REGISTRY_URL, emptyVct, urlMappings);
    }
}












