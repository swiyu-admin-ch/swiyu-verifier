/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.eid.didtoolbox.Jwk;
import ch.admin.eid.didtoolbox.VerificationMethod;
import ch.admin.eid.didtoolbox.VerificationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.List;

import static ch.admin.bj.swiyu.verifier.common.base64.Base64Utils.decodeMultibaseKey;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

/**
 * This class is responsible for loading the public key of an issuer from a JWT Token. The issuer is identified by its
 * DID (Decentralized Identifier) which is stored in the <code>iss</code> claim of the JWT Token. The public key is
 * identified by the <code>kid</code> attribute in the header of the JWT Token.
 * <p>
 * See SD-JWT-based Verifiable Credentials (SD-JWT VC) specification
 * <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-04.html#section-3.5">Section 3.5 (did document
 * resolution)</a>
 */
@AllArgsConstructor
@Service
@Slf4j
public class IssuerPublicKeyLoader {

    /**
     * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
     */
    private final DidResolverAdapter didResolverAdapter;
    private final ObjectMapper objectMapper;

    /**
     * Generates a public key from the given multibase key. The public key is encoded
     * according to the X.509 standard.
     *
     * @param multibaseKey a <a href="https://github.com/multiformats/multibase?tab=readme-ov-file#multibase-table">multibase key</a>
     * @return the public key encoded according to the X.509 standard
     * @throws IllegalArgumentException if the key generation fails due to an invalid key specification or missing algorithm
     */
    private static PublicKey parsePublicKeyOfTypeMultibaseKey(String multibaseKey) {
        if (!hasText(multibaseKey)) {
            throw new IllegalArgumentException("Failed to parse multibase key from verification method since no multibase key was provided");
        }
        try {
            var decodedKey = decodeMultibaseKey(multibaseKey);
            var keyFactory = KeyFactory.getInstance("EC");
            var keySpec = new X509EncodedKeySpec(decodedKey);
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Failed to generate public key from specification due to missing algorithm", e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to generate public key from specification due to invalid key spec", e);
        }
    }

    /**
     * Loads the public key of the issuer with the given <code>issuer</code> and <code>kid</code>.
     *
     * @return The public key of the issuer.
     * @throws LoadingPublicKeyOfIssuerFailedException if the public key could not be loaded
     */
    public PublicKey loadPublicKey(String issuer, String kid) throws LoadingPublicKeyOfIssuerFailedException {
        try {
            log.trace("Fetching Public Key {} for issuer {}", kid, issuer);
            VerificationMethod method = loadVerificationMethod(issuer, kid);
            return parsePublicKey(method);
        } catch (RuntimeException e) {
            throw new LoadingPublicKeyOfIssuerFailedException("Failed to lookup public key from JWT Token for issuer %s and kid %s".formatted(issuer, kid), e);
        }
    }

    /**
     * Loads the DID document of the given <code>issuerDidTdw</code> from the base registry and returns its
     * <a href="https://www.w3.org/TR/did-core/#verification-method-properties">verification method </a> for the given
     * <code>issuerKeyId</code>. The verification method contains the public key of the issuer.
     *
     * @param issuerDidTdw the decentralized identifier of the issuer
     * @param issuerKeyId  the key id (in jwt token header provided as 'kid' attribute) indicating which verification method to use
     * @return The VerificationMethod The base64 encoded public key of the issuer as it is mentioned in the <code>verificationMethod</code> for the given issuerKeyId.
     * @throws DidResolverException  if the DID document could not be resolved
     * @throws IllegalStateException if the DID document does not contain any matching verification method for the given issuerKeyId
     */
    private VerificationMethod loadVerificationMethod(String issuerDidTdw, String issuerKeyId) throws DidResolverException, IllegalStateException {
        try (var didDoc = didResolverAdapter.resolveDid(issuerDidTdw)) {
            // Step 1: get all verification methods within the document
            var verificationMethods = didDoc.getVerificationMethod();
            if (isEmpty(verificationMethods)) {
                throw new IllegalStateException(("Could not resolve public key from issuer %s since its resolved DID " +
                        "document does not contain any verification methods").formatted(issuerDidTdw));
            }
            log.trace("Resolved did document for issuer {}", issuerDidTdw);
            // Step 2: find the right method matching the key
            var method = verificationMethods.stream()
                    .filter(m -> m.getId().equals(issuerKeyId))
                    .findFirst();
            if (method.isEmpty()) {
                throw new IllegalStateException(("Could not resolve public key from issuer %s since its resolved DID " +
                        "document does not contain any public keys for the key %s").formatted(issuerDidTdw, issuerKeyId));
            }
            log.trace("Found Verification Method {}", issuerDidTdw);
            return method.get();
        }
    }

    /**
     * Generates a public key from the given base64 encoded public key.
     *
     * @param method the verification method containing the public key
     * @return the public key
     * @throws IllegalArgumentException if the key generation fails due to an invalid key specification,
     *                                  missing algorithm or unsupported encoding type
     */
    private PublicKey parsePublicKey(VerificationMethod method) {
        return switch (method.getVerificationType()) {
            case VerificationType.MULTIKEY -> parsePublicKeyOfTypeMultibaseKey(method.getPublicKeyMultibase());
            case VerificationType.JSON_WEB_KEY2020 -> parsePublicKeyOfTypeJsonWebKey(method.getPublicKeyJwk());
            default -> throw new IllegalArgumentException("Unsupported encoding type: " + method.getVerificationType() +
                    ". Only Multikey and JsonWebKey2020 are supported");
        };
    }

    /**
     * Generates a public key from the given JSON Web Key (JWK).
     *
     * @param jwk a json web token
     * @return the public key
     */
    private PublicKey parsePublicKeyOfTypeJsonWebKey(Jwk jwk) {
        if (jwk == null) {
            throw new IllegalArgumentException("Failed to parse Json Web Key from verification method since no jwk was provided");
        }
        try {
            String json = objectMapper.writeValueAsString(jwk);
            return ECKey.parse(json).toECPublicKey();
        } catch (JsonProcessingException | JOSEException | ParseException e) {
            throw new IllegalArgumentException("Failed to parse json web token", e);
        }
    }

    public List<String> loadTrustStatement(String trustRegistryUri, String issuerDidTdw) throws JsonProcessingException {
        log.debug("Resolving trust statement at registry {} for {}", trustRegistryUri, issuerDidTdw);
        var rawTrustStatements = didResolverAdapter.resolveTrustStatement("%s/api/v1/truststatements/", issuerDidTdw);
        return objectMapper.readValue(rawTrustStatements, List.class);
    }
}