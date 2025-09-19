package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.api.definition.PresentationDefinitionDto;
import ch.admin.bj.swiyu.verifier.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.api.management.ResponseModeDto;
import ch.admin.bj.swiyu.verifier.api.management.dcql.DcqlQueryDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagementServiceTest {

    private ManagementRepository repository;
    private ApplicationProperties applicationProperties;
    private ManagementService service;
    private UUID id;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        repository = mock(ManagementRepository.class);
        applicationProperties = mock(ApplicationProperties.class);
        service = new ManagementService(repository, applicationProperties);
    }

    @Test
    void createVerificationManagement_thenSuccess() {
        var presentationDefinitionDto = mock(PresentationDefinitionDto.class);
        var presentationDefinition = mock(PresentationDefinition.class);
        CreateVerificationManagementDto requestDto = new CreateVerificationManagementDto(
                List.of("did:example:123"),
                null,
                false,
                ResponseModeDto.DIRECT_POST,
                presentationDefinitionDto,
                null,
                null
        );
        var management = mock(Management.class);
        when(repository.save(any(Management.class))).thenReturn(management);

        try (MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toPresentationDefinition(any(PresentationDefinitionDto.class)))
                    .thenReturn(presentationDefinition);
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(any(Management.class), any()))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.api.management.ManagementResponseDto.class));

            service.createVerificationManagement(requestDto);

            managementMapper.verify(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties), times(1));
        }

        verify(repository).save(any(Management.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createVerificationManagementDcql_thenSuccess(boolean useBoth) {
        var presentationDefinitionDto = mock(PresentationDefinitionDto.class);
        var presentationDefinition = mock(PresentationDefinition.class);
        var dcqlQueryDto = mock(DcqlQueryDto.class);
        var dcqlQuery = mock(DcqlQuery.class);
        CreateVerificationManagementDto requestDto = new CreateVerificationManagementDto(
                List.of("did:example:123"),
                null,
                false,
                ResponseModeDto.DIRECT_POST,
                presentationDefinitionDto,
                null,
                useBoth ? dcqlQueryDto : null
        );
        var management = mock(Management.class);
        when(repository.save(any(Management.class))).thenReturn(management);

        try (MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toPresentationDefinition(any(PresentationDefinitionDto.class)))
                    .thenReturn(presentationDefinition);
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(any(Management.class), any()))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.api.management.ManagementResponseDto.class));
            try (MockedStatic<DcqlMapper> dcqlMapper = mockStatic(DcqlMapper.class)) {
                dcqlMapper.when(() -> DcqlMapper.toDcqlQuery(any(DcqlQueryDto.class)))
                        .thenReturn(dcqlQuery);
                service.createVerificationManagement(requestDto);

                managementMapper.verify(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties), times(1));
            }
        }
        verify(repository).save(any(Management.class));
    }

    @Test
    void createVerificationManagement_whenNoDcqlOrPE_thenFailure() {
        CreateVerificationManagementDto requestDto = new CreateVerificationManagementDto(
                List.of("did:example:123"),
                null,
                false,
                ResponseModeDto.DIRECT_POST,
                null,
                null,
                null
        );
        var error = assertThrows(IllegalArgumentException.class, () -> service.createVerificationManagement(requestDto));
        assertEquals("PresentationDefinition must be provided", error.getMessage());
    }

    @Test
    void createVerificationManagementWithNullRequest_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> service.createVerificationManagement(null));
    }

    @Test
    void createVerificationManagementWithNullPresentation_throwsException() {
        var request = mock(CreateVerificationManagementDto.class);
        when(request.presentationDefinition()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.createVerificationManagement(request));
    }

    @Test
    void getManagement_thenSuccess() {
        var management = mock(Management.class);
        when(management.isExpired()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(management));

        try (MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.api.management.ManagementResponseDto.class));

            service.getManagement(id);
            managementMapper.verify(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties), times(1));
        }

        verify(repository, never()).deleteById(any());
    }

    @Test
    void getManagement_throwsException() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(VerificationNotFoundException.class, () -> service.getManagement(id));
    }

    @Test
    void getManagementWithExpired_shouldDelete() {
        var management = mock(Management.class);
        when(management.isExpired()).thenReturn(true);
        when(management.getId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(management));
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null));
        service.getManagement(id);
        verify(repository).deleteById(id);
    }

    @Test
    void removeExpiredManagements_shouldDelete() {
        service.removeExpiredManagements();
        verify(repository).deleteByExpiresAtIsBefore(anyLong());
    }

    @Test
    void createManagementWithDirectPostJwt_thenSuccess() {
        var presentationDefinitionDto = mock(PresentationDefinitionDto.class);
        var presentationDefinition = mock(PresentationDefinition.class);
        var dcqlQueryDto = mock(DcqlQueryDto.class);
        var dcqlQuery = mock(DcqlQuery.class);
        CreateVerificationManagementDto requestDto = new CreateVerificationManagementDto(
                List.of("did:example:123"),
                null,
                false,
                ResponseModeDto.DIRECT_POST_JWT,
                presentationDefinitionDto,
                null,
                dcqlQueryDto
        );
        var management = mock(Management.class);
        var managementCaptor = ArgumentCaptor.forClass(Management.class);
        when(repository.save(any(Management.class))).thenReturn(management);

        try (MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toPresentationDefinition(any(PresentationDefinitionDto.class)))
                    .thenReturn(presentationDefinition);
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(any(Management.class), any()))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.api.management.ManagementResponseDto.class));
            try (MockedStatic<DcqlMapper> dcqlMapper = mockStatic(DcqlMapper.class)) {
                dcqlMapper.when(() -> DcqlMapper.toDcqlQuery(any(DcqlQueryDto.class)))
                        .thenReturn(dcqlQuery);
                service.createVerificationManagement(requestDto);

                managementMapper.verify(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties), times(1));
            }
        }

        verify(repository).save(managementCaptor.capture());
        var savedManagement = managementCaptor.getValue();
        var responseSpec = savedManagement.getResponseSpecification();
        assertThat(responseSpec.getEncryptedResponseEncValuesSupported()).isNotEmpty();
        assertThat(responseSpec.getJwks()).isNotEmpty();
        assertThat(responseSpec.getJwksPrivate()).isNotEmpty();
        JWKSet jwkSet = assertDoesNotThrow(() -> JWKSet.parse(responseSpec.getJwks()));
        assertThat(jwkSet.containsNonPublicKeys()).isFalse();
        JWKSet jwkSetPrivate = assertDoesNotThrow(() -> JWKSet.parse(responseSpec.getJwksPrivate()));
        assertThat(jwkSetPrivate.containsNonPublicKeys()).isTrue();

        // Validate that keys can be indeed be used together by doing a dry run of the encryption
        for (JWK jwk : jwkSet.getKeys()) {
            // This part would be done by the wallet
            var encryptionMethod = EncryptionMethod.parse(responseSpec.getEncryptedResponseEncValuesSupported().getFirst());
            JWEObject jweObject = new JWEObject(
                    new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, encryptionMethod).keyID(jwk.getKeyID()).build(),
                    new Payload("Test")
            );
            assertDoesNotThrow(() -> jweObject.encrypt(new ECDHEncrypter(jwk.toECKey())));

            var encryptedObject = jweObject.serialize();

            // In the verifier we decrypt now with the private key
            var parsedJWE = assertDoesNotThrow(() -> JWEObject.parse(encryptedObject));
            var privateKey = assertDoesNotThrow(() -> jwkSetPrivate.getKeyByKeyId(parsedJWE.getHeader().getKeyID()).toECKey());
            assertDoesNotThrow(() -> parsedJWE.decrypt(new ECDHDecrypter(privateKey)));
            assertEquals("Test", parsedJWE.getPayload().toString());
        }

    }
}