package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.eid.did_sidekicks.DidSidekicksException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

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
    public static final String TRUST_STATEMENT_ISSUANCE_ENDPOINT = "/api/v1/truststatements/issuance";

    private final DidResolverFacade didResolverFacade;
    private final ObjectMapper objectMapper;

    public JWK loadJWK(String kid) throws LoadingPublicKeyOfIssuerFailedException {
        log.trace("Fetching Public Key {} ", kid);
        try {
            return loadVerificationMethod(kid);
        } catch (DidResolverException | DidSidekicksException | IllegalArgumentException e) {
            throw new LoadingPublicKeyOfIssuerFailedException("Failed to lookup public key from JWT Token for kid %s".formatted(kid), e);
        }
    }

    /**
     * Loads the DID document of the given <code>issuerDid</code> from the base registry and returns its
     * <a href="https://www.w3.org/TR/did-core/#verification-method-properties">verification method </a> for the given
     * <code>issuerKeyId</code>. The verification method contains the public key of the issuer.
     *
     * @param issuerKeyId the key id (in jwt token header provided as 'kid' attribute) indicating which verification method to use
     * @return The VerificationMethod The base64 encoded public key of the issuer as it is mentioned in the <code>verificationMethod</code> for the given issuerKeyId.
     * @throws DidResolverException     if the DID document could not be resolved
     * @throws IllegalArgumentException if the issuerKeyId is invalid
     * @throws DidSidekicksException    if the DID document does not contain any matching verification method for the given issuerKeyId
     */
    private JWK loadVerificationMethod(String issuerKeyId) throws DidResolverException, IllegalArgumentException, DidSidekicksException {
        return didResolverFacade.resolveKey(issuerKeyId);
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
}