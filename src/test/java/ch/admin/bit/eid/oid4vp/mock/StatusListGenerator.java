package ch.admin.bit.eid.oid4vp.mock;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;

import java.util.Date;

/**
 * Generates status lists for tests
 */
@AllArgsConstructor
public class StatusListGenerator {

    /**
     * Example status list from the <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-status-list-02.html#section-9.1">spec</a>
     * <pre><code>
     * status[0] = 1
     * status[1] = 2
     * status[2] = 0
     * status[3] = 3</code></pre>
     */
    public static final String SPEC_STATUS_LIST = "eNo76fITAAPfAgc";

    private final ECKey key;
    private final String issuerId;
    private final String keyId;

    /**
     * <pre><code>
     * {
     * "alg": "ES256",
     * "kid": "12",
     * "typ": "statuslist+jwt"
     * }
     * .
     * {
     * "iat": 1686920170,
     * "iss": "https://example.com",
     * "status_list": {
     * "bits": 2,
     * "lst": "eNo76fITAAPfAgc"
     * },
     * "sub": "https://example.com/statuslists/1"
     * }
     * </code></pre>
     */
    public String createTokenStatusListTokenVerifiableCredential(String statusList) throws JOSEException {
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuerId)
                .subject("https://example.com/statuslists/1")
                .claim("status_list", new JWTClaimsSet.Builder()
                        .claim("bits", 2)
                        .claim("lst", statusList)
                        .issueTime(new Date())
                        .build()
                        .toJSONObject()).build();
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("statuslist+jwt"))
                .keyID(keyId)
                .build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }
}
