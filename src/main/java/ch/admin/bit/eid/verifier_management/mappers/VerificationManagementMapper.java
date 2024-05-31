package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.dto.CreateVerificationResponseDto;
import ch.admin.bit.eid.verifier_management.models.dto.GetVerificationResponseDto;
import ch.admin.bit.eid.verifier_management.models.entities.VerificationManagement;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VerificationManagementMapper {

    public static CreateVerificationResponseDto verificationManagementToGetVerificationResponseDto(VerificationManagement verification) {

        if (verification == null) {
            throw new IllegalArgumentException("VerificationManagement is null");
        }

        return CreateVerificationResponseDto.builder()
                .id(verification.getId())
                .authorizationRequestObjectUri(verification.getAuthorizationRequestObjectUri())
                .authorizationRequestId(verification.getAuthorizationRequestId())
                .status(verification.getStatus())
                .expiresAt(verification.)
                .build();
    }

    public static GetVerificationResponseDto verificationManagementToCreateVerificationResponseDto(
            VerificationManagement verification,
            AuthorizationResponseData authorizationResponseData) {

        if (verification == null) {
            throw new IllegalArgumentException("VerificationManagement is null");
        }

        return GetVerificationResponseDto.builder()
                .id(verification.getId())
                .authorization_request_object_uri(verification.getAuthorizationRequestObjectUri())
                .authorization_request_id(verification.getAuthorizationRequestId())
                .status(verification.getStatus())
                .authorization_response_data(authorizationResponseData)
                .build();
    }
}
