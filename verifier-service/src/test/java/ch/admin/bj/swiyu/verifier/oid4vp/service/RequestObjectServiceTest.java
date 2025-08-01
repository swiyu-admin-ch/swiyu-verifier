package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.api.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.api.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignerProvider;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestObjectServiceTest {

    private final UUID mgmtId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenidClientMetadataDto openidClientMetadataDto = new OpenidClientMetadataDto();
    private final String clientId = "client-id";
    private ManagementRepository managementRepository;
    private SignerProvider signerProvider;
    private RequestObjectService service;

    @BeforeEach
    void setUp() {
        var applicationProperties = mock(ApplicationProperties.class);
        var openIdClientMetadataConfiguration = mock(OpenIdClientMetadataConfiguration.class);

        managementRepository = mock(ManagementRepository.class);
        signerProvider = mock(SignerProvider.class);

        service = new RequestObjectService(
                applicationProperties,
                openIdClientMetadataConfiguration,
                managementRepository,
                objectMapper,
                signerProvider
        );

        // Mock application configurations
        when(applicationProperties.getRequestObjectVersion()).thenReturn("1.0");
        when(applicationProperties.getClientId()).thenReturn(clientId);
        when(applicationProperties.getClientIdScheme()).thenReturn("test-scheme");
        when(applicationProperties.getExternalUrl()).thenReturn("https://test");
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn("did:example:123#key1");
        when(openIdClientMetadataConfiguration.getOpenIdClientMetadata()).thenReturn(openidClientMetadataDto);
    }

    @Test
    void assembleRequestObjectWithSignedJWT_thenSuccess() throws Exception {
        mockManagement(true);

        when(signerProvider.canProvideSigner()).thenReturn(true);
        JWSSigner jwsSigner = new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate());
        when(signerProvider.getSigner()).thenReturn(jwsSigner);

        Object result = service.assembleRequestObject(mgmtId);

        assertThat(result).isInstanceOf(String.class);

        SignedJWT jwt = SignedJWT.parse((String) result);
        assertEquals("oauth-authz-req+jwt", jwt.getHeader().getType().toString());
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(clientId);
    }

    @Test
    void assembleRequestObjectUnsigned_thenSuccess() {
        mockManagement(false);

        Object result = service.assembleRequestObject(mgmtId);

        assertThat(result).isInstanceOf(RequestObjectDto.class);
        RequestObjectDto dto = (RequestObjectDto) result;
        assertThat(dto.getClientId()).isEqualTo(clientId);
    }

    @Test
    void assembleRequestObjectNotPending_throwsException() {
        var management = mock(Management.class);
        when(managementRepository.findById(mgmtId)).thenReturn(Optional.of(management));
        when(management.isVerificationPending()).thenReturn(false);

        ProcessClosedException exception = assertThrows(ProcessClosedException.class, () -> {
            service.assembleRequestObject(mgmtId);
        });

        assertEquals("Verification Process has already been closed.", exception.getMessage());
    }

    @Test
    void assembleRequestObjectExpired_throwsException() {
        var management = mock(Management.class);
        when(managementRepository.findById(mgmtId)).thenReturn(Optional.of(management));
        when(management.isVerificationPending()).thenReturn(true);
        when(management.isExpired()).thenReturn(true);

        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> {
            service.assembleRequestObject(mgmtId);
        });

        assertEquals("Verification Request with id %s is expired".formatted(mgmtId), exception.getMessage());
    }

    @Test
    void assembleRequestObjectNoSigner_throwsException() {
        mockManagement(true);

        when(signerProvider.canProvideSigner()).thenReturn(false);

        assertThatThrownBy(() -> service.assembleRequestObject(mgmtId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no signing key");
    }

    private void mockManagement(boolean needsJwsAuthorizationRequest) {
        var management = mock(Management.class);
        when(managementRepository.findById(mgmtId)).thenReturn(Optional.of(management));
        when(management.isVerificationPending()).thenReturn(true);
        when(management.isExpired()).thenReturn(false);
        when(management.getRequestedPresentation()).thenReturn(null);
        when(management.getRequestNonce()).thenReturn("nonce");
        when(management.getJwtSecuredAuthorizationRequest()).thenReturn(needsJwsAuthorizationRequest);
    }
}