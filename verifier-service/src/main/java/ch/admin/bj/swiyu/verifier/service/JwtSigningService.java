package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.jwssignatureservice.factory.strategy.KeyStrategyException;
import ch.admin.bj.swiyu.jwtutil.JwtUtil;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import ch.admin.bj.swiyu.verifier.common.profile.SwissProfileVersions;
import ch.admin.bj.swiyu.verifier.common.util.SignerProvider;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class JwtSigningService {

    private static final String OAUTH_AUTHZ_REQ_JWT = "oauth-authz-req+jwt";
    private final ApplicationProperties applicationProperties;
    private final JwsSignatureFacade jwsSignatureFacade;


    /**
     * Signs the provided JWT claims set using the specified or default signing configuration.
     * If keyId and keyPin are provided, they override the default key settings; otherwise, defaults are used.
     *
     * @param claimsSet the JWT claims to sign
     * @param keyId optional key ID to override the default; if null, default is used
     * @param keyPin optional key PIN to override the default; if null, default is used
     * @param verificationMethod the verification method for the JWS header (e.g., a DID URL)
     * @return the signed JWT object
     * @throws IllegalArgumentException if invalid arguments are provided
     * @throws IllegalStateException if the signer provider cannot be initialized or no signing key is available
     * @throws JOSEException if the signing operation fails
     */
    public SignedJWT signJwt(JWTClaimsSet claimsSet, String keyId, String keyPin, String verificationMethod)
            throws IllegalArgumentException, JOSEException {

        SignerProvider signerProvider;
        try {
            signerProvider = createSignerProvider(keyId, keyPin);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize signature provider. This is probably because the key could not be loaded.", e);
        }

        if (!signerProvider.canProvideSigner()) {
            log.error("Upstream system error. Upstream system requested presentation to be signed despite the verifier not being configured for it");
            throw new IllegalStateException("Presentation was configured to be signed, but no signing key was configured.");
        }

        // Build the JAR header with the required swiss profile version indication.
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(verificationMethod)
                .type(new JOSEObjectType(OAUTH_AUTHZ_REQ_JWT))
                .customParam(SwissProfileVersions.PROFILE_VERSION_PARAM, SwissProfileVersions.VERIFICATION_PROFILE_VERSION)
                .build();

        return JwtUtil.signJwt(claimsSet, header, signerProvider.getSigner());
    }

    /**
     * Creates a {@link SignerProvider} based on the default application configuration,
     * optionally overriding the hardware security module (HSM) key identifier and PIN.
     * * <p>If {@code keyId} or {@code keyPin} are provided (not null and not empty),
     * they selectively replace the default values specified in the application properties.</p>
     *
     * @param keyId  The specific key identifier to use, or {@code null}/empty to fall back to the default.
     * @param keyPin The specific key PIN to use, or {@code null}/empty to fall back to the default.
     * @return A fully configured {@link SignerProvider} ready for JWT signing operations.
     * @throws KeyStrategyException If the underlying signature strategy cannot be initialized.
     */
    private SignerProvider createSignerProvider(String keyId, String keyPin) throws IllegalArgumentException, KeyStrategyException {
        var defaultSignatureConfiguration = toSignatureConfiguration(applicationProperties);
        return new SignerProvider(jwsSignatureFacade.createSigner(defaultSignatureConfiguration, keyId, keyPin));
    }

    /**
     * Mapping allowing to use same logic as issuer service in most parts
     *
     * @return SignatureConfiguration built from the given application properties
     */
    private static SignatureConfiguration toSignatureConfiguration(
            ApplicationProperties applicationProperties) {
        return SignatureConfiguration.builder()
                .keyManagementMethod(applicationProperties.getKeyManagementMethod())
                .privateKey(applicationProperties.getSigningKey())
                .hsm(applicationProperties.getHsm())
                .pkcs11Config(applicationProperties.getHsm().getPkcs11Config())
                .verificationMethod(applicationProperties.getSigningKeyVerificationMethod())
                .build();
    }
}
