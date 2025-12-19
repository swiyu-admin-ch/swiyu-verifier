package ch.admin.bj.swiyu.verifier.dto.management;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AcceptedIssuerDidsOrTrustAnchorsNotEmptyValidatorTest {
    private final AcceptedIssuerDidsOrTrustAnchorsNotEmptyValidator validator = new AcceptedIssuerDidsOrTrustAnchorsNotEmptyValidator();

    @Test
    void isValid_returnsFalse_whenBothAreNullOrEmpty() {

        CreateVerificationManagementDto dto1 = CreateVerificationManagementDto.builder().build();
        CreateVerificationManagementDto dto2 = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of())
                .trustAnchors(List.of())
                .build();
        assertFalse(validator.isValid(dto1, null));
        assertFalse(validator.isValid(dto2, null));
    }

    @Test
    void isValid_returnsTrue_whenAcceptedIssuerDidsNotEmpty() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder().acceptedIssuerDids(List.of("did:example:123")).build();
        assertTrue(validator.isValid(dto, null));
    }

    @Test
    void isValid_returnsTrue_whenTrustAnchorsNotEmpty() {
        TrustAnchorDto trustAnchor = new TrustAnchorDto("did:example:anchor", "this-is-a-uri");
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder().trustAnchors(List.of(trustAnchor)).build();
        assertTrue(validator.isValid(dto, null));
    }

    @Test
    void isValid_returnsTrue_whenBothNotEmpty() {
        TrustAnchorDto trustAnchor = new TrustAnchorDto("did:example:anchor", "this-is-a-uri");
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .trustAnchors(List.of(trustAnchor))
                .acceptedIssuerDids(List.of("did:example:123"))
                .build();
        assertTrue(validator.isValid(dto, null));
    }
}