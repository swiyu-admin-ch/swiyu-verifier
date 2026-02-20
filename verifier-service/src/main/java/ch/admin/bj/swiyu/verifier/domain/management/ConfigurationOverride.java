package ch.admin.bj.swiyu.verifier.domain.management;

import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

@Builder
public record ConfigurationOverride(
        String externalUrl,
        String verifierDid,
        String verificationMethod,
        String keyId,
        String keyPin) {

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
