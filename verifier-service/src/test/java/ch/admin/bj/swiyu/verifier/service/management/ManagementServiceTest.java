package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.dto.VerificationClientErrorDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlQueryDto;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        ManagementTransactionalService managementTransactionalService = new ManagementTransactionalService(repository, applicationProperties);
        service = new ManagementService(applicationProperties, managementTransactionalService, null);
    }

    @Test
    void createVerificationManagement_withDCQL_thenSuccess() {
        var dcqlQueryDto = mock(DcqlQueryDto.class);
        var dcqlQuery = mock(DcqlQuery.class);
        CreateVerificationManagementDto requestDto = new CreateVerificationManagementDto(
                List.of("did:example:123"),
                null,
                false,
                ResponseModeTypeDto.DIRECT_POST,
                null,
                dcqlQueryDto,
                null,
                null
        );
        var management = mock(Management.class);
        when(repository.save(any(Management.class))).thenReturn(management);

        try (MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(any(Management.class), any()))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto.class));
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
    void createVerificationManagement_whenNoDCQL_thenFailure() {
        CreateVerificationManagementDto requestDto = new CreateVerificationManagementDto(
                List.of("did:example:123"),
                null,
                false,
                ResponseModeTypeDto.DIRECT_POST,
                null,
                null,
                null,
                null
        );
        var error = assertThrows(IllegalArgumentException.class, () -> service.createVerificationManagement(requestDto));
        assertEquals("dcql_query is required", error.getMessage());
    }

    @Test
    void createVerificationManagement_withNullRequest_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> service.createVerificationManagement(null));
    }

    @Test
    void getManagementResponseDto_thenSuccess() {
        var management = mock(Management.class);
        when(management.isExpired()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(management));

        try (MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto.class));

            service.getManagementResponseDto(id);
            managementMapper.verify(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties), times(1));
        }

        verify(repository, never()).deleteById(any());
    }

    @Test
    void getManagementResponseDto_withUnknownId_throwsException() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(VerificationNotFoundException.class, () -> service.getManagementResponseDto(id));
    }

    @Test
    void getManagementResponseDto_withExpired_shouldDelete() {
        var management = mock(Management.class);
        when(management.isExpired()).thenReturn(true);
        when(management.getId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(management));
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null, null));
        assertThrows(VerificationNotFoundException.class, () -> service.getManagementResponseDto(id));
        verify(repository).deleteById(id);
    }

    @Test
    void removeExpiredManagements_shouldDelete() {
        service.removeExpiredManagements();
        verify(repository).deleteByExpiresAtIsBefore(anyLong());
    }

    @Test
    void createVerificationManagement_withDirectPostJwt_thenSuccess() {
        var dcqlQueryDto = mock(DcqlQueryDto.class);
        var dcqlQuery = mock(DcqlQuery.class);
        CreateVerificationManagementDto requestDto = new CreateVerificationManagementDto(
                List.of("did:example:123"),
                null,
                false,
                ResponseModeTypeDto.DIRECT_POST_JWT,
                null,
                dcqlQueryDto,
                null,
                null
        );
        var management = mock(Management.class);
        var managementCaptor = ArgumentCaptor.forClass(Management.class);
        when(repository.save(any(Management.class))).thenReturn(management);

        try (MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(any(Management.class), any()))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto.class));
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
            assertThat(jwk.getAlgorithm()).as("For OID4VP algorithm MUST be not null").isNotNull();
            assertThat(jwk.getAlgorithm()).as("For swiss profile verification 1.0 only ECDH-ES is supported").isEqualTo(JWEAlgorithm.ECDH_ES);
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

        @Test
        void markVerificationSucceeded_shouldReturnRedirectURI() {
            var transactionalService = mock(ManagementTransactionalService.class);
            var mgmtService = new ManagementService(applicationProperties, transactionalService, null);
            var managementId = UUID.randomUUID();
            var expected = URI.create("https://wallet.example/callback?response_code=abc");
            when(transactionalService.markVerificationSucceeded(managementId, "credentialData")).thenReturn(expected);
            var dto = mgmtService.markVerificationSucceeded(managementId, "credentialData");
            assertThat(dto.redirectURI()).isEqualTo(expected);
            verify(transactionalService).markVerificationSucceeded(managementId, "credentialData");
        }

        @Test
        void markVerificationFailedDueToClientRejection_shouldReturnNullRedirect_andPersistFailure() {
            var transactionalService = mock(ManagementTransactionalService.class);
            var mgmtService = new ManagementService(applicationProperties, transactionalService, null);
            var managementId = UUID.randomUUID();
            var rejection = new VerificationPresentationRejectionDto(VerificationClientErrorDto.CLIENT_REJECTED, "reason");
            doNothing().when(transactionalService).markVerificationFailedDueToClientRejection(managementId, rejection);
            var dto = mgmtService.markVerificationFailedDueToClientRejection(managementId, rejection);
            assertThat(dto.redirectURI()).isNull();
            verify(transactionalService).markVerificationFailedDueToClientRejection(managementId, rejection);
        }
    }