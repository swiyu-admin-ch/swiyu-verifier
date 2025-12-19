package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import lombok.experimental.UtilityClass;
import org.springframework.util.CollectionUtils;

/**
 * Validator for CreateVerificationManagementDto requests.
 */
@UtilityClass
public class CreateVerificationManagementValidator {

    /**
     * Validates the CreateVerificationManagementDto request.
     * @param request the request to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(CreateVerificationManagementDto request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateVerificationManagement is null");
        }
        if (request.presentationDefinition() == null) {
            // Until the wallet is migrated we MUST provide a presentation definition.
            throw new IllegalArgumentException("PresentationDefinition must be provided");
        }

        var dcqlQueryDto = request.dcqlQuery();
        if (dcqlQueryDto != null) {
            if (dcqlQueryDto.credentials().stream().anyMatch(cred -> Boolean.TRUE.equals(cred.multiple()))) {
                // Currently supporting only 1 vp token per credential query
                throw new IllegalArgumentException("multiple credentials in response for a single query not supported");
            }
            if (!CollectionUtils.isEmpty(dcqlQueryDto.credentialSets())) {
                // Not yet supporting credential sets
                throw new IllegalArgumentException("credential sets not yet supported");
            }
            if (dcqlQueryDto.credentials().stream().anyMatch(cred -> cred.meta().vctValues().isEmpty())) {
                throw new IllegalArgumentException("vct_values is required");
            }
        }
    }
}
