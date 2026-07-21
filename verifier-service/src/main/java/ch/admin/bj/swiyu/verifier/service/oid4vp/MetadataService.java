package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.service.management.ManagementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetadataService {

    private final OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;

    public OpenidClientMetadataDto getOpenidClientMetadataForManagementEntity(Management mgmt, ResponseSpecification responseSpecification) {

        var clientMetadata = openIdClientMetadataConfiguration.getVerifierMetadata();

        // create deep copy of metadata to not bother with the metadata bean
        var clientMetadataClone = clientMetadata.toBuilder().build();

        // Enrich client metadata for DIRECT_POST_JWT response mode as required by the protocol
        addDirectPostJWTConfigIfNecessary(clientMetadataClone, responseSpecification);

        // Build a per-request copy of client_metadata so that per-verification overrides
        // (e.g. client_name, logo_uri, client_id) never mutate the global singleton map.
        return overrideDefaultsIfNecessary(mgmt.getConfigurationOverride().clientMetadata(), clientMetadataClone);
    }

    private void addDirectPostJWTConfigIfNecessary(OpenidClientMetadataDto dto,
                                                   ResponseSpecification responseSpecification) {
        if (ResponseModeType.DIRECT_POST_JWT.equals(responseSpecification.getResponseModeType())) {
            dto.setJwks(ManagementMapper.toJWKSetDto(responseSpecification.getJwks()));
            dto.setEncryptedResponseEncValuesSupported(responseSpecification.getEncryptedResponseEncValuesSupported());
        }
    }

    private OpenidClientMetadataDto overrideDefaultsIfNecessary(Map<String, String> overrides,
                                                                OpenidClientMetadataDto openidClientMetadataDto) {
        if (overrides != null && !overrides.isEmpty()) {
            var additionalProps = openidClientMetadataDto.getAdditionalProperties();
            HashMap<String, Object> patchedProps = additionalProps != null ? new HashMap<>(additionalProps) : new HashMap<>();
            patchedProps.putAll(overrides);
            openidClientMetadataDto.setAdditionalProperties(patchedProps);
        }

        return openidClientMetadataDto;
    }
}
