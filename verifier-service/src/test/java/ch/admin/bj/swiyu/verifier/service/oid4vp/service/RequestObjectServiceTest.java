package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.profile.SwissProfileVersions;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.util.SignerProvider;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.domain.management.*;
import ch.admin.bj.swiyu.verifier.service.JwtSigningService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.MetadataService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectService;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestObjectServiceTest {

    private final String nonce = "nonce";
    private final UUID mgmtId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenidClientMetadataDto openidClientMetadataDto = new OpenidClientMetadataDto();
    private final String clientId = "client-id";
    private ManagementRepository managementRepository;
    private SignerProvider signerProvider;
    private RequestObjectService service;
    private JwtSigningService jwtSigningService;
    private final String prefix = "test-prefix";
    private ApplicationProperties applicationProperties;

    @BeforeEach
    void setUp() {
        applicationProperties = mock(ApplicationProperties.class);
        managementRepository = mock(ManagementRepository.class);
        jwtSigningService = mock(JwtSigningService.class);
        signerProvider = mock(SignerProvider.class);

        var metadataService = mock(MetadataService.class);

        service = new RequestObjectService(
                applicationProperties,
                managementRepository,
                objectMapper,
                jwtSigningService,
                metadataService,
                Optional.empty()
        );
        
        // Mock application configurations
        when(applicationProperties.getClientId()).thenReturn(clientId);
        when(applicationProperties.getClientIdPrefix()).thenReturn(prefix);
        when(applicationProperties.getExternalUrl()).thenReturn("https://test");
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn("did:example:123#key1");
        when(applicationProperties.getRequestObjectTTLSeconds()).thenReturn( 600);
        when(metadataService.getOpenidClientMetadataForManagementEntity(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(openidClientMetadataDto);
    }
    
    @Test
    void assembleRequestObjectWithSignedJWT_thenSuccess() throws Exception {
        var mockedManagement = mockManagement(true);
        String keyId = "did:example:123#key1";

        when(signerProvider.canProvideSigner()).thenReturn(true);
        JWSSigner jwsSigner = new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate());
        when(signerProvider.getSigner()).thenReturn(jwsSigner);
        when(jwtSigningService.signJwt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            var claimsSet = invocation.getArgument(0, JWTClaimsSet.class);
            JWSHeader header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256)
                    .keyID(keyId)
                    .type(new com.nimbusds.jose.JOSEObjectType("oauth-authz-req+jwt"))
                    .customParam(SwissProfileVersions.PROFILE_VERSION_PARAM, SwissProfileVersions.VERIFICATION_PROFILE_VERSION)
                    .build();
            SignedJWT signedJwt = new SignedJWT(header, claimsSet);
            signedJwt.sign(jwsSigner);
            return signedJwt;
        });

        String jwtString = service.assembleRequestObject(mgmtId);

        SignedJWT jwt = SignedJWT.parse(jwtString);
        // verify JWT header
        assertEquals("oauth-authz-req+jwt", jwt.getHeader().getType().toString());
        assertEquals(SwissProfileVersions.VERIFICATION_PROFILE_VERSION, jwt.getHeader().getCustomParam(SwissProfileVersions.PROFILE_VERSION_PARAM));
        // verify JWT body
        String clientIdWithPrefix = prefix + ":" + clientId;
        var claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).as("iss claim is REQUIRED and SHOULD be present with prefix").isEqualTo(clientIdWithPrefix);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(keyId);
        var state = claims.getClaim("state");
        assertThat(state)
            .as("state should be set as of swiss-profile-verification 1.0").isNotNull()
            .as("state should match the one provided in management object").isEqualTo(mockedManagement.getOauthState());
        assertThat(claims.getAudience()).isEqualTo(List.of(RequestObjectService.AUDIENCE));
        assertThat(claims.getClaim("nonce")).isEqualTo(nonce);
        assertThat(claims.getClaim("response_mode")).isEqualTo(ResponseModeTypeDto.DIRECT_POST.toString());
        assertThat(claims.getClaim("client_metadata")).isNotNull();
        assertThat(claims.getClaim("response_type")).isEqualTo(RequestObjectService.RESPONSE_TYPE);
        assertThat(claims.getIssueTime()).isNotNull().isBeforeOrEqualTo(Instant.now());
        assertThat(claims.getExpirationTime()).isNotNull().isAfter(Instant.now());
    }

    @Test
    void assembleRequestObjectWithSignedJWT_whenOverridden_thenSuccess() throws Exception {
        var externalUrl = "https://overriden.example.com";
        var overrideDid = "did:override";
        var verificationMethod = "did:override#key1";
        var management = mockManagement(true);
        var logoUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVQI12NgAAIABQAABjE+ibYAAAAASUVORK5CYII=";
        var clientMetadata = Map.of(
                "client_name", "Override Client",
                "logo_uri", logoUri
        );
        var override = new ConfigurationOverride(externalUrl, overrideDid, verificationMethod, null, null, clientMetadata);
        when(management.getConfigurationOverride()).thenReturn(override);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        JWSSigner jwsSigner = new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate());
        when(signerProvider.getSigner()).thenReturn(jwsSigner);
        when(jwtSigningService.signJwt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> {
            var claimsSet = invocation.getArgument(0, JWTClaimsSet.class);
            JWSHeader header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256)
                    .keyID("did:override#key1")
                    .type(new com.nimbusds.jose.JOSEObjectType("oauth-authz-req+jwt"))
                    .customParam(SwissProfileVersions.PROFILE_VERSION_PARAM, SwissProfileVersions.VERIFICATION_PROFILE_VERSION)
                    .build();
            SignedJWT signedJwt = new SignedJWT(header, claimsSet);
            signedJwt.sign(jwsSigner);
            return signedJwt;
        });

        String jwtString = service.assembleRequestObject(mgmtId);

        SignedJWT jwt = SignedJWT.parse(jwtString);
        assertEquals("oauth-authz-req+jwt", jwt.getHeader().getType().toString());
        assertEquals(SwissProfileVersions.VERIFICATION_PROFILE_VERSION, jwt.getHeader().getCustomParam(SwissProfileVersions.PROFILE_VERSION_PARAM));
        var claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer())
            .as("iss claim MUST be present using the override value")
            .isEqualTo(prefix + ":" + overrideDid);
        assertThat(jwt.getHeader().getKeyID())
            .as("Override Verificaiton method must be present to validate the jwt")
            .isEqualTo(verificationMethod);
        assertThat(claims.getClaim("response_uri").toString()).startsWith(externalUrl);
    }

    @Test
    void assembleRequestObject_withoutClientPrefix_thenSuccess() throws Exception {
        mockManagement(true);

        when(applicationProperties.getClientIdPrefix()).thenReturn(null);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        JWSSigner jwsSigner = new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate());
        when(signerProvider.getSigner()).thenReturn(jwsSigner);
        when(jwtSigningService.signJwt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            var claimsSet = invocation.getArgument(0, JWTClaimsSet.class);
            JWSHeader header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256)
                    .keyID("did:example:123#key1")
                    .type(new com.nimbusds.jose.JOSEObjectType("oauth-authz-req+jwt"))
                    .customParam(SwissProfileVersions.PROFILE_VERSION_PARAM, SwissProfileVersions.VERIFICATION_PROFILE_VERSION)
                    .build();
            SignedJWT signedJwt = new SignedJWT(header, claimsSet);
            signedJwt.sign(jwsSigner);
            return signedJwt;
        });

        String jwtString = service.assembleRequestObject(mgmtId);

        SignedJWT jwt = SignedJWT.parse(jwtString);
        assertThat(jwt.getJWTClaimsSet().getClaimAsString("client_id")).isEqualTo(clientId);
    }

    @Test
    void assembleRequestObject_whenOverridde_thenSuccess() throws ParseException, JOSEException {
        var externalUrl = "https://overriden.example.com";
        var overrideDid = "did:override";
        var overrideDidResult = prefix + ":" + overrideDid;
        var management = mock(Management.class);
        when(managementRepository.findById(mgmtId)).thenReturn(Optional.of(management));
        when(management.isVerificationPending()).thenReturn(true);
        when(management.isExpired()).thenReturn(false);
        when(management.getRequestNonce()).thenReturn(nonce);
        when(management.getJwtSecuredAuthorizationRequest()).thenReturn(false);
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null, null));
        when(management.getOauthState()).thenReturn(UUID.randomUUID().toString());
        JWSSigner jwsSigner = new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate());
        when(jwtSigningService.signJwt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            var claimsSet = invocation.getArgument(0, JWTClaimsSet.class);
            JWSHeader header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256)
                    .keyID("did:example:123#key1")
                    .type(new com.nimbusds.jose.JOSEObjectType("oauth-authz-req+jwt"))
                    .customParam(SwissProfileVersions.PROFILE_VERSION_PARAM, SwissProfileVersions.VERIFICATION_PROFILE_VERSION)
                    .build();
            SignedJWT signedJwt = new SignedJWT(header, claimsSet);
            signedJwt.sign(jwsSigner);
            return signedJwt;
        });


        var responseSpecification = mock(ResponseSpecification.class);
        when(management.getResponseSpecification()).thenReturn(responseSpecification);
        when(responseSpecification.getResponseModeType()).thenReturn(ResponseModeType.DIRECT_POST_JWT);

        var ephemeralEncryptionKey = assertDoesNotThrow(() -> new ECKeyGenerator(Curve.P_256)
                .keyID(UUID.randomUUID().toString())
                .algorithm(JWEAlgorithm.ECDH_ES)
                .generate());
        JWKSet jwkSet = new JWKSet(ephemeralEncryptionKey);
        when(responseSpecification.getJwks()).thenReturn(jwkSet.toString());
        when(responseSpecification.getEncryptedResponseEncValuesSupported()).thenReturn(List.of());

        var logoUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVQI12NgAAIABQAABjE+ibYAAAAASUVORK5CYII=";
        var clientMetadata = Map.of(
                "client_name", "Override Client",
                "logo_uri", logoUri
        );
        var override = new ConfigurationOverride(externalUrl, overrideDid, null, null, null, clientMetadata);
        when(management.getConfigurationOverride()).thenReturn(override);

        String jwtString = service.assembleRequestObject(mgmtId);

        SignedJWT jwt = SignedJWT.parse(jwtString);
        var claims = jwt.getJWTClaimsSet();
        assertThat(claims.getStringClaim("client_id")).as("DID was overridden").isEqualTo(overrideDidResult);
        assertThat(claims.getStringClaim("response_uri")).as("Was using overridden external url").startsWith(externalUrl);
    }

    @Test
    void assembleRequestObjectNotPending_throwsException() {
        var management = mock(Management.class);
        when(managementRepository.findById(mgmtId)).thenReturn(Optional.of(management));
        when(management.isVerificationPending()).thenReturn(false);

        ProcessClosedException exception = assertThrows(ProcessClosedException.class, () -> service.assembleRequestObject(mgmtId));

        assertEquals("Verification Process has already been closed.", exception.getMessage());
    }

    @Test
    void assembleRequestObjectExpired_throwsException() {
        var management = mock(Management.class);
        when(managementRepository.findById(mgmtId)).thenReturn(Optional.of(management));
        when(management.isVerificationPending()).thenReturn(true);
        when(management.isExpired()).thenReturn(true);

        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> service.assembleRequestObject(mgmtId));

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
        when(management.getRequestNonce()).thenReturn(nonce);
        when(management.getJwtSecuredAuthorizationRequest()).thenReturn(needsJwsAuthorizationRequest);
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null, null));
        var responseVerification = mock(ResponseSpecification.class);
        when(management.getResponseSpecification()).thenReturn(responseVerification);
        when(responseVerification.getResponseModeType()).thenReturn(ResponseModeType.DIRECT_POST);
        when(management.getOauthState()).thenReturn(UUID.randomUUID().toString());
        return management;
    }
}

