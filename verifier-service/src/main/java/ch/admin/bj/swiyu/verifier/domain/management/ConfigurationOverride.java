package ch.admin.bj.swiyu.verifier.domain.management;

import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Builder
public record ConfigurationOverride(
        String externalUrl,
        String verifierDid,
        String verificationMethod,
        String keyId,
        String keyPin,
        /**
         * Optional per-verification overrides for individual client_metadata fields embedded in the
         * signed authorization request JWT. Keys follow OID4VP naming, including locale-tagged
         * variants (e.g. {@code client_name#en}, {@code logo_uri}). When present, these entries
         * take precedence over the values loaded from the configured client-metadata-file.
         */
        Map<String, Object> clientMetadata) {

    public String externalUrlOrDefault(String defaultValue) {
        return StringUtils.getIfBlank(externalUrl, () -> defaultValue);
    }

    public String verifierDidOrDefault(String defaultValue) {
        return StringUtils.getIfBlank(verifierDid, () -> defaultValue);
    }

    public String verificationMethodOrDefault(String defaultValue) {
        return StringUtils.getIfBlank(verificationMethod, () -> defaultValue);
    }
}
