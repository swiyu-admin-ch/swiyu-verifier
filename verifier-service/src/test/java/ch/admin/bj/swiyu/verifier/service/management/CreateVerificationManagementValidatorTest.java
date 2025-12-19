package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.api.definition.PresentationDefinitionDto;
import ch.admin.bj.swiyu.verifier.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.api.management.TrustAnchorDto;
import ch.admin.bj.swiyu.verifier.api.management.dcql.DcqlCredentialDto;
import ch.admin.bj.swiyu.verifier.api.management.dcql.DcqlCredentialMetaDto;
import ch.admin.bj.swiyu.verifier.api.management.dcql.DcqlQueryDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateVerificationManagementValidatorTest {

    @Test
    void validate_shouldThrow_whenRequestIsNull() {
        assertThrows(IllegalArgumentException.class, () -> CreateVerificationManagementValidator.validate(null));
    }

    @Test
    void validate_shouldThrow_whenPresentationDefinitionIsNull() {
        var request = createRequest(null, null, null);
        assertThrows(IllegalArgumentException.class, () -> CreateVerificationManagementValidator.validate(request));
    }

    @Test
    void validate_shouldNotThrow_whenRequestIsValid() {
        var request = createRequest(createPresentationDefinition(), null, List.of());
        assertDoesNotThrow(() -> CreateVerificationManagementValidator.validate(request));
    }

    @Test
    void validate_shouldThrow_whenDcqlQueryHasMultipleCredentials() {
        List<DcqlCredentialDto> credentials = List.of(
                new DcqlCredentialDto(
                        null, // id
                        null, // format
                        true, // multiple
                        new DcqlCredentialMetaDto(List.of(List.of("vct")), null, null), // meta
                        null, // claims
                        null, // claimSets
                        null, // requireCryptographicHolderBinding
                        null // trustedAuthorities
                )
        );
        var dcqlQuery = new DcqlQueryDto(credentials, List.of());
        var request = createRequest(createPresentationDefinition(), dcqlQuery, null);
        assertThrows(IllegalArgumentException.class, () -> CreateVerificationManagementValidator.validate(request));
    }

    @Test
    void validate_shouldNotThrow_whenDcqlQueryIsValid() {
        List<DcqlCredentialDto> credentials = List.of(
                new DcqlCredentialDto(
                        null, // id
                        null, // format
                        false, // multiple
                        new DcqlCredentialMetaDto(null, List.of("vct"), null), // meta
                        null, // claims
                        null, // claimSets
                        null, // requireCryptographicHolderBinding
                        null // trustedAuthorities
                )
        );
        var dcqlQuery = new DcqlQueryDto(credentials, List.of());
        var request = createRequest(createPresentationDefinition(), dcqlQuery, null);
        assertDoesNotThrow(() -> CreateVerificationManagementValidator.validate(request));
    }

    private CreateVerificationManagementDto createRequest(PresentationDefinitionDto presentationDefinition,
                                                          DcqlQueryDto dcqlQuery,
                                                          List<TrustAnchorDto> trustAnchors) {
        return new CreateVerificationManagementDto(
                null, // acceptedIssuerDids
                trustAnchors,
                null, // jwtSecuredAuthorizationRequest
                null, // responseMode
                presentationDefinition,
                null, // configuration_override
                dcqlQuery
        );
    }

    private PresentationDefinitionDto createPresentationDefinition() {
        return new PresentationDefinitionDto("id", "name", "purpose", null, List.of());
    }
}
