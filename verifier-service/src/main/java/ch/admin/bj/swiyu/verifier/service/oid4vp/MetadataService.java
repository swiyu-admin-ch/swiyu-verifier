package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.service.management.ManagementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MetadataService {

    private final OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;
    private final ObjectMapper objectMapper;

    public OpenidClientMetadataDto getOpenidClientMetadataForManagementEntity(Management mgmt, ResponseSpecification responseSpecification) {

        // create deep copy of metadata to not bother with the metadata bean
        var clientMetadataClone = getOpenidClientMetadata();

        // Enrich client metadata for DIRECT_POST_JWT response mode as required by the protocol
        clientMetadataClone.addDirectPostJWTConfigIfNecessary(
                ManagementMapper.toResponseModeDto(responseSpecification.getResponseModeType()),
                responseSpecification.getJwks() != null ? ManagementMapper.toJWKSetDto(responseSpecification.getJwks()) : null,
                responseSpecification.getEncryptedResponseEncValuesSupported());

        // Build a per-request copy of client_metadata so that per-verification overrides
        // (e.g. client_name, logo_uri, client_id) never mutate the global singleton map.
        clientMetadataClone.overrideDefaultsIfNecessary(mgmt.getConfigurationOverride().clientMetadata());

        return clientMetadataClone;
    }

    public OpenidClientMetadataDto getOpenidClientMetadata() {
        var clientMetadata = openIdClientMetadataConfiguration.getVerifierMetadata();

        // create deep copy of metadata to not bother with the metadata bean
        return deepCopy(clientMetadata);
    }

    public OpenidClientMetadataDto deepCopy(OpenidClientMetadataDto original) {
        return objectMapper.convertValue(original, OpenidClientMetadataDto.class);
    }
}