package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.jwssignatureservice.JwsSignatureService;
import ch.admin.bj.swiyu.jwssignatureservice.dto.HSMPropertiesDto;
import ch.admin.bj.swiyu.jwssignatureservice.dto.SignatureConfigurationDto;
import ch.admin.bj.swiyu.jwssignatureservice.factory.strategy.KeyStrategyException;
import ch.admin.bj.swiyu.verifier.SignatureConfiguration;
import ch.admin.bj.swiyu.verifier.common.config.HSMProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignatureConfigurationWithHsm;
import ch.admin.bj.swiyu.verifier.common.exception.ConfigurationException;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import com.nimbusds.jose.JWSSigner;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.JWS_SIGNER_CACHE;

/**
 * This service is used to create a signer for the given signature configuration.
 * It uses the KeyManagementStrategyFactory to create the signer based on the key management method.
 * <p>
 * The signer is cached to avoid creating it multiple times.
 */
@Slf4j
@Service
@AllArgsConstructor
public class JwsSignatureFacade {

    private final JwsSignatureService jwsSignatureService;


    /**
     * Create Signer with overridden overrideKeyId & overrideKeyPin
     */
    @Cacheable(JWS_SIGNER_CACHE)
    public JWSSigner createSigner(@NotNull SignatureConfigurationWithHsm signatureConfigurationWithHsm, ConfigurationOverride override) throws KeyStrategyException {

        validateNotNull(signatureConfigurationWithHsm, override);

        SignatureConfiguration resolvedConfig = resolveConfiguration(signatureConfigurationWithHsm, override);
        SignatureConfigurationDto dto = mapToDto(resolvedConfig);

        return jwsSignatureService.createSigner(dto, override.keyId(), override.keyPin());
    }

    /**
     * Searches available configs for the appropriate one for the verification method.
     * @param baseConfig Base configuration in which the search will take place
     * @param override which may contain an alternative verification method
     * @return baseConfig if no override verificationMethod is used or if an HSM is being used.
     *         When using key from application config the apropriate config is returned.
     */
    private SignatureConfiguration resolveConfiguration(SignatureConfigurationWithHsm baseConfig, ConfigurationOverride override) {
        if (StringUtils.isEmpty(override.verificationMethod())) {
            return baseConfig;
        }

        // handles default key
        if (baseConfig.getVerificationMethod().equals(override.verificationMethod())) {
            return baseConfig;
        }

        // handles hsm using JCA (Java Cryptography Architecture) or PKCS#11 use
        if (baseConfig.supportsHSM() && override.keyId() != null) {
            return baseConfig;
        }

        // handles signing keys from application config file
        return findMatchingSigningKey(baseConfig, override.verifierDid(), override.verificationMethod());
    }

    private SignatureConfiguration findMatchingSigningKey(SignatureConfigurationWithHsm baseConfig, String verifierDid, String verificationMethod) {
        if (verifierDid == null || verificationMethod == null || !baseConfig.supportsSigningKeys()) {
            throw new ConfigurationException("No signing key found for verification method: " + verificationMethod);
        }

        // warn if verification method does not start with the same did
        if (!verificationMethod.startsWith(verifierDid)) {
            log.warn("Verification method {} does not start with issuer DID: {}", verificationMethod, verifierDid);
        }

        return baseConfig.getSigningKeys().stream()
                .filter(key -> key.getVerificationMethod().equals(verificationMethod))
                .findFirst()
                .orElseThrow(() -> new ConfigurationException("No signing key found for verification method: " + verificationMethod));
    }


    /**
     * Ensures input are not null
     * @param signatureConfigurationWithHsm config to be validated
     * @param override override to be validated
     * @throws ConfigurationException if one of the inputs was null
     */
    private void validateNotNull(SignatureConfigurationWithHsm signatureConfigurationWithHsm, ConfigurationOverride override) {
        if (signatureConfigurationWithHsm == null) {
            throw new ConfigurationException("Signature configuration cannot be null.");
        }

        if (override == null) {
            throw new ConfigurationException("Configuration override cannot be null.");
        }
    }

    private HSMPropertiesDto mapHsmProperties(HSMProperties hsm) {
        if (hsm == null) {
            return null;
        }

        return HSMPropertiesDto.builder()
                .userPin(hsm.getUserPin())
                .keyId(hsm.getKeyId())
                .keyPin(hsm.getKeyPin())
                .pkcs11Config(hsm.getPkcs11Config())
                .user(hsm.getUser())
                .host(hsm.getHost())
                .port(hsm.getPort())
                .password(hsm.getPassword())
                .proxyUser(hsm.getProxyUser())
                .proxyPassword(hsm.getProxyPassword())
                .build();
    }

    private SignatureConfigurationDto mapToDto(SignatureConfiguration config) {
        return SignatureConfigurationDto.builder()
                .keyManagementMethod(config.getKeyManagementMethod())
                .privateKey(config.getPrivateKey())
                .hsm(mapHsmProperties(config.getHsm()))
                .pkcs11Config(config.getPkcs11Config())
                .verificationMethod(config.getVerificationMethod())
                .build();
    }

}