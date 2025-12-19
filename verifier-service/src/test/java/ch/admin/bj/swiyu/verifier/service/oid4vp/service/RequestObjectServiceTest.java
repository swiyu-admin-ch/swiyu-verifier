package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.dto.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.util.SignerProvider;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.domain.management.*;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.service.JwtSigningService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectResult;
import ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
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
    private JwtSigningService jwtSigningService;

    @BeforeEach
    void setUp() {
        var applicationProperties = mock(ApplicationProperties.class);
        var openIdClientMetadataConfiguration = mock(OpenIdClientMetadataConfiguration.class);

        managementRepository = mock(ManagementRepository.class);
        jwtSigningService = mock(JwtSigningService.class);
        signerProvider = mock(SignerProvider.class);

        service = new RequestObjectService(
                applicationProperties,
                openIdClientMetadataConfiguration,
                managementRepository,
                objectMapper,
                jwtSigningService
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
        when(jwtSigningService.signJwt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("did:example:123#key1")
        )).thenAnswer(invocation -> {
            var claimsSet = invocation.getArgument(0, JWTClaimsSet.class);
            JWSHeader header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256)
                    .keyID("did:example:123#key1")
                    .type(new com.nimbusds.jose.JOSEObjectType("oauth-authz-req+jwt"))
                    .build();
            SignedJWT signedJwt = new SignedJWT(header, claimsSet);
            signedJwt.sign(jwsSigner);
            return signedJwt;
        });

        RequestObjectResult result = service.assembleRequestObject(mgmtId);

        assertThat(result).isInstanceOf(RequestObjectResult.Signed.class);
        assertThat(result.isSigned()).isTrue();

        String jwtString = ((RequestObjectResult.Signed) result).jwt();
        SignedJWT jwt = SignedJWT.parse(jwtString);
        assertEquals("oauth-authz-req+jwt", jwt.getHeader().getType().toString());
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(clientId);
    }

    @Test
    void assembleRequestObjectWithSignedJWT_whenOverridden_thenSuccess() throws Exception {
        var externalUrl = "https://overriden.example.com";
        var overrideDid = "did:override";
        var verificationMethod = "did:override#key1";
        var management = mockManagement(true);
        var override = new ConfigurationOverride(externalUrl, overrideDid, verificationMethod, null, null);
        when(management.getConfigurationOverride()).thenReturn(override);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        JWSSigner jwsSigner = new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate());
        when(signerProvider.getSigner()).thenReturn(jwsSigner);
        when(jwtSigningService.signJwt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("did:override#key1")
        )).thenAnswer(invocation -> {
            var claimsSet = invocation.getArgument(0, JWTClaimsSet.class);
            JWSHeader header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256)
                    .keyID("did:override#key1")
                    .type(new com.nimbusds.jose.JOSEObjectType("oauth-authz-req+jwt"))
                    .build();
            SignedJWT signedJwt = new SignedJWT(header, claimsSet);
            signedJwt.sign(jwsSigner);
            return signedJwt;
        });

        RequestObjectResult result = service.assembleRequestObject(mgmtId);

        assertThat(result).isInstanceOf(RequestObjectResult.Signed.class);
        assertThat(result.isSigned()).isTrue();

        String jwtString = ((RequestObjectResult.Signed) result).jwt();
        SignedJWT jwt = SignedJWT.parse(jwtString);
        assertEquals("oauth-authz-req+jwt", jwt.getHeader().getType().toString());
        assertEquals(verificationMethod, jwt.getHeader().getKeyID());
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(overrideDid);
        assertThat(jwt.getJWTClaimsSet().getClaim("response_uri").toString()).startsWith(externalUrl);

    }

    @Test
    void assembleRequestObjectUnsigned_thenSuccess() {
        mockManagement(false);

        RequestObjectResult result = service.assembleRequestObject(mgmtId);

        assertThat(result).isInstanceOf(RequestObjectResult.Unsigned.class);
        assertThat(result.isSigned()).isFalse();

        RequestObjectDto dto = ((RequestObjectResult.Unsigned) result).requestObject();
        assertThat(dto.getClientId()).isEqualTo(clientId);
    }

    @Test
    void assembleRequestObjectUnsigned_whenOverrideParameters_thenSuccess() {
        var externalUrl = "https://overriden.example.com";
        var overrideDid = "did:override";
        var management = mockManagement(false);
        var override = new ConfigurationOverride(externalUrl, overrideDid, null, null, null);
        when(management.getConfigurationOverride()).thenReturn(override);

        RequestObjectResult result = service.assembleRequestObject(mgmtId);

        assertThat(result).isInstanceOf(RequestObjectResult.Unsigned.class);
        assertThat(result.isSigned()).isFalse();

        RequestObjectDto dto = ((RequestObjectResult.Unsigned) result).requestObject();
        assertEquals(overrideDid, dto.getClientId());
        assertThat(dto.getResponseUri()).startsWith(externalUrl);

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
                .hasMessageContaining("Failed to sign request object");
    }

    private Management mockManagement(boolean needsJwsAuthorizationRequest) {
        var management = mock(Management.class);
        when(managementRepository.findById(mgmtId)).thenReturn(Optional.of(management));
        when(management.isVerificationPending()).thenReturn(true);
        when(management.isExpired()).thenReturn(false);
        when(management.getRequestedPresentation()).thenReturn(null);
        when(management.getRequestNonce()).thenReturn("nonce");
        when(management.getJwtSecuredAuthorizationRequest()).thenReturn(needsJwsAuthorizationRequest);
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null));
        var responseVerification = mock(ResponseSpecification.class);
        when(management.getResponseSpecification()).thenReturn(responseVerification);
        when(responseVerification.getResponseModeType()).thenReturn(ResponseModeType.DIRECT_POST);
        return management;
    }
}