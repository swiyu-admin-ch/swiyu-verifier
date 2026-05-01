package ch.admin.bj.swiyu.verifier.service.publickey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
/**
 * Loads trust statements from a trust registry via {@link DidResolverFacade}.
 * <p>
 * Public-key loading for JWT signature verification has been replaced by
 * {@link ch.admin.bj.swiyu.sdjwtvalidator.SdJwtVcValidator} (Flow B), which performs
 * DID-based resolution with URL-allowlist enforcement.
 * </p>
 */
@AllArgsConstructor
@Service
@Slf4j
public class IssuerPublicKeyLoader {
    /** Path suffix for the trust-statement issuance endpoint on a trust registry. */
    public static final String TRUST_STATEMENT_ISSUANCE_ENDPOINT = "/api/v1/truststatements/issuance";
    private final DidResolverFacade didResolverFacade;
    private final ObjectMapper objectMapper;
    /**
     * Loads the raw trust-statement JWTs for the given VCT from the trust registry.
     *
     * @param trustRegistryUri URI of the trust registry to be used
     * @param vct              the VCT for which the TrustStatementIssuance is to be loaded
     * @return a list of TrustStatementIssuance raw JWTs
     * @throws JsonProcessingException if the response cannot be parsed
     */
    public List<String> loadTrustStatement(String trustRegistryUri, String vct) throws JsonProcessingException {
        log.debug("Resolving trust statement at registry {} for {}", trustRegistryUri, vct);
        var rawTrustStatements = didResolverFacade.resolveTrustStatement(
                trustRegistryUri + TRUST_STATEMENT_ISSUANCE_ENDPOINT, vct);
        return objectMapper.readValue(rawTrustStatements, List.class);
    }
}
