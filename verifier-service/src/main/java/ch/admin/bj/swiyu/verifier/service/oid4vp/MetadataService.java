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
        clientMetadataClone.addDirectPostJWTConfigIfNecessary(
                ManagementMapper.toResponseModeDto(responseSpecification.getResponseModeType()),
                responseSpecification.getJwks() != null ? ManagementMapper.toJWKSetDto(responseSpecification.getJwks()) : null,
                responseSpecification.getEncryptedResponseEncValuesSupported());

        // Build a per-request copy of client_metadata so that per-verification overrides
        // (e.g. client_name, logo_uri, client_id) never mutate the global singleton map.
        clientMetadataClone.overrideDefaultsIfNecessary(mgmt.getConfigurationOverride().clientMetadata());

        return clientMetadataClone;
    }
}
