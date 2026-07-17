package ch.admin.bj.swiyu.verifier.domain.management;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
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
        Map<String, String> clientMetadata) {

    public String externalUrlOrDefault(String defaultValue) {
        return StringUtils.getIfBlank(externalUrl, () -> defaultValue);
    }

    public String verifierDidOrDefault(String defaultValue) {
        return StringUtils.getIfBlank(verifierDid, () -> defaultValue);
    }

    /**
     * Resolve the effective verifier identifier, applying an optional client-id prefix.
     * <p>This method returns this override's verifier DID if present, otherwise it falls back to {@code properties.getClientId()}. If {@code properties.getClientIdPrefix()} is non-blank, the prefix is prepended to the resolved DID separated by a colon (i.e. {@code prefix:did}).</p>
     * <p>Note: this method does not validate DID syntax. If {@code properties} is {@code null}, a {@link NullPointerException} will be thrown by the caller of the properties accessor.</p>
     *
     * @param properties application properties providing the client id and optional prefix
     * @return the effective verifier identifier, either the raw DID or the prefixed form {@code prefix:did}
     */
    public String verifierDidOrDefaultWithPrefix(ApplicationProperties properties) {
        var did = verifierDidOrDefault(properties.getClientId());
        var prefix = properties.getClientIdPrefix();

        if (StringUtils.isBlank(prefix)) {
            return did;
        }

        return prefix + ":" + did;
    }

    public String verificationMethodOrDefault(String defaultValue) {
        return StringUtils.getIfBlank(verificationMethod, () -> defaultValue);
    }
}
