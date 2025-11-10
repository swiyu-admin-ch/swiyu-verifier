package ch.admin.bj.swiyu.verifier.domain.management;

import lombok.Builder;

@Builder
public record ConfigurationOverride(
        String externalUrl,
        String verifierDid,
        String verificationMethod,
        String keyId,
        String keyPin
) {
}
