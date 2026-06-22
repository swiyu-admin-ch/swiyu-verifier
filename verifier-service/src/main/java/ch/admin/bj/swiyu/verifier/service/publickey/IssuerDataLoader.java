package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import ch.admin.eid.did_sidekicks.Jwk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * Loads issuer-related data required for credential verification via DID-based resolution.
 * <p>
 * Provides two capabilities:
 * <ul>
 *   <li>Loading a {@link JWK} for a given issuer DID and absolute {@code kid} header value,
 *       used by trust protocol 2 signature verification.</li>
 *   <li>Loading raw trust statement JWTs from a trust registry endpoint,
 *       used by trust protocol 1 validation.</li>
 * </ul>
 * Key resolution is performed exclusively via the {@code kid} header (absolute DID URL),
 * in accordance with PARENT-ADR-027 / PARENT-ADR-035. The {@code iss} claim is not used
 * for key resolution.
 */
@AllArgsConstructor
@Service
@Slf4j
public class IssuerDataLoader {

    /**
     * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
     */
    public static final String TRUST_STATEMENT_ISSUANCE_ENDPOINT = "/api/v1/truststatements/issuance";

    private final DidResolverFacade didResolverFacade;
    private final ObjectMapper objectMapper;


    public JWK loadJWK(String issuerDid, String kid) throws LoadingPublicKeyOfIssuerFailedException {
        log.trace("Fetching Public Key {} ", kid);
        try {
        Jwk resolverJwk = loadVerificationMethod(issuerDid, kid);
        return parseJwk(resolverJwk, issuerDid);
        } catch (DidResolverException | DidSidekicksException | IllegalArgumentException e) {
            throw new LoadingPublicKeyOfIssuerFailedException("Failed to lookup public key from JWT Token for issuer %s and kid %s".formatted(issuerDid, kid), e);
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
        Jwk jwk = didResolverFacade.resolveDid(issuerDidId, fragment);
        log.trace("Resolved did document for issuer {} and found Verification Method {}", issuerDidId, issuerKeyId);
        return jwk;
    }



    /**
     * Parses the did document jwk to a nimbus jwk. 
     * <br>
     * Extends the kid to be swiss-profile-anchor compatible.
     * Normally the kids would match exactly (string.equals). 
     * In swiss profile anchor it was though defined that the kid in JWT must be the full did with the JWK kid as fragment.
     * <br>
     * <code>"kid": "{issuerDid}#{verificationMethodKid}" </code>
     */
    private JWK parseJwk(Jwk jwk, String issuerDid) throws LoadingPublicKeyOfIssuerFailedException {
        if (jwk == null) {
            throw new IllegalArgumentException("Failed to parse Json Web Key from verification method since no jwk was provided");
        }
        try {
            Map<String, Object> json = objectMapper.convertValue(jwk, new TypeReference<>() {});
            json.put("kid", issuerDid+"#"+jwk.getKid());
            // Create kid as used in swiss-profile-anchor
            return JWK.parse(json);
        } catch (ParseException e) {
            throw new LoadingPublicKeyOfIssuerFailedException("Failed to parse json web token", e);
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
        var rawTrustStatements = didResolverFacade.resolveTrustStatement(trustRegistryUri + TRUST_STATEMENT_ISSUANCE_ENDPOINT, vct);
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