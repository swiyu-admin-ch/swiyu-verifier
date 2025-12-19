/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.eid.did_sidekicks.DidSidekicksException;
import ch.admin.eid.did_sidekicks.Jwk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.text.ParseException;
import java.util.List;

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
     * Loads the public key of the issuer with the given <code>issuer</code> and <code>kid</code>.
     *
     * @return The public key of the issuer.
     * @throws LoadingPublicKeyOfIssuerFailedException if the public key could not be loaded
     */
    public PublicKey loadPublicKey(String issuer, String kid) throws LoadingPublicKeyOfIssuerFailedException {
        try {
            log.trace("Fetching Public Key {} for issuer {}", kid, issuer);
            Jwk method = loadVerificationMethod(issuer, kid);
            return parsePublicKeyOfTypeJsonWebKey(method);
        } catch (DidResolverException | DidSidekicksException | IllegalArgumentException e) {
            throw new LoadingPublicKeyOfIssuerFailedException("Failed to lookup public key from JWT Token for issuer %s and kid %s".formatted(issuer, kid), e);
        }
    }

    /**
     * Loads the DID document of the given <code>issuerDid</code> from the base registry and returns its
     * <a href="https://www.w3.org/TR/did-core/#verification-method-properties">verification method </a> for the given
     * <code>issuerKeyId</code>. The verification method contains the public key of the issuer.
     *
     * @param issuerDidId the decentralized identifier of the issuer
     * @param issuerKeyId  the key id (in jwt token header provided as 'kid' attribute) indicating which verification method to use
     * @return The VerificationMethod The base64 encoded public key of the issuer as it is mentioned in the <code>verificationMethod</code> for the given issuerKeyId.
     * @throws DidResolverException  if the DID document could not be resolved
     * @throws IllegalArgumentException if the issuerKeyId is invalid
     * @throws DidSidekicksException if the DID document does not contain any matching verification method for the given issuerKeyId
     */
    private Jwk loadVerificationMethod(String issuerDidId, String issuerKeyId) throws DidResolverException, IllegalArgumentException, DidSidekicksException {
        var fragment = extractFragment(issuerKeyId);
        try (var didDoc = didResolverAdapter.resolveDid(issuerDidId)) {
            log.trace("Resolved did document for issuer {}", issuerDidId);
            var jwk = didDoc.getKey(fragment);
            log.trace("Found Verification Method {}", issuerKeyId);
            return jwk;
        }
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

    /**
     *
     * @param trustRegistryUri URI of the trust registry to be used
     * @param vct The VCT for which the TrustStatementIssuance is to be loaded
     * @return A list of TrustStatementIssuance as raw JWTs
     * @throws JsonProcessingException if something is wrong with the format of the response
     */
    public List<String> loadTrustStatement(String trustRegistryUri, String vct) throws JsonProcessingException {
        log.debug("Resolving trust statement at registry {} for {}", trustRegistryUri, vct);
        var rawTrustStatements = didResolverAdapter.resolveTrustStatement("%s/api/v1/truststatements/issuance", vct);
        return objectMapper.readValue(rawTrustStatements, List.class);
    }

    /**
     *
     * @param keyId reference to a verification method in a did doc
     * @return fragment of the keyId
     * @throws IllegalArgumentException if the provided keyId does not have a fragment
     */
    private static String extractFragment(String keyId) throws IllegalArgumentException {
        var keyIdSplit = keyId.split("#"); // should be in the format of <did id>#<fragment>
        final int expectedLength = 2;
        if (keyIdSplit.length != expectedLength) {
            throw new IllegalArgumentException(String.format("Key %s is malformed: missing fragment", keyId));
        }
        return keyIdSplit[1];
    }
}
