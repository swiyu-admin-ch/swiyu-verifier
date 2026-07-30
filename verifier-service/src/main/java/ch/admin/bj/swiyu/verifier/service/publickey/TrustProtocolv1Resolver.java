package ch.admin.bj.swiyu.verifier.service.publickey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
@Deprecated(since = "Trust Protocol 2.0")
@AllArgsConstructor
@Service
@Slf4j
public class TrustProtocolv1Resolver {

    /**
     * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
     */
    public static final String TRUST_STATEMENT_ISSUANCE_ENDPOINT = "/api/v1/truststatements/issuance";

    private final DidResolverFacade didResolverFacade;
    private final ObjectMapper objectMapper;


    /**
     *
     * @param trustRegistryUri URI of the trust registry to be used
     * @param vct The VCT for which the TrustStatementIssuance is to be loaded
     * @return A list of TrustStatementIssuance as raw JWTs
     * @throws JsonProcessingException if something is wrong with the format of the response
     */
    @Deprecated(since = "Trust Protocol 2.0")
    public List<String> loadTrustStatement(String trustRegistryUri, String vct) throws JsonProcessingException {
        log.debug("Resolving trust statement at registry {} for {}", trustRegistryUri, vct);
        var rawTrustStatements = didResolverFacade.resolveTrustStatement(trustRegistryUri + TRUST_STATEMENT_ISSUANCE_ENDPOINT, vct);
        return objectMapper.readValue(rawTrustStatements, List.class);
    }
}