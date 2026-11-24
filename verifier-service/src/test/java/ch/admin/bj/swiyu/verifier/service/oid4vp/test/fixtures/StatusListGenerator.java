package ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures;

import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolver;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.Date;

import static org.mockito.Mockito.when;

/**
 * Generates status lists for tests
 */
@UtilityClass
public class StatusListGenerator {

    /**
     * Example status list from the <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-status-list-02.html#section-9.1">spec</a>
     * <pre><code>
     * status[0] = 1 - revoked
     * status[1] = 2 - suspended
     * status[2] = 0 - valid
     * status[3] = 3 - custom status
     * </code></pre>
     */
    public static final String SPEC_STATUS_LIST = "eNo76fITAAPfAgc";

    /**
     * Subject of the example status list, also used as URI when stubbing the status list resolution.
     */
    public static final String SPEC_SUBJECT = "https://example.com/statuslists/1";
    private final static JWTClaimsSet.Builder JWT_CLAIM_SET_BUILDER = new JWTClaimsSet.Builder().subject(SPEC_SUBJECT);

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
    public static String createTokenStatusListTokenVerifiableCredential(String statusList, ECKey signingKey, String issuerId, String keyId) throws JOSEException {
        var claims = JWT_CLAIM_SET_BUILDER
                .issuer(issuerId)
                .issueTime(new Date())
                .claim("exp", Instant.now().plusSeconds(3600).getEpochSecond())
                .claim("ttl", 180)
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
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    /**
     * Creates a token status list token whose {@code status_list.bits} value is not one of 1, 2, 4 or 8.
     *
     * @param signingKey the key used to sign the token status list token
     * @param issuerId   the {@code iss} claim of the token status list token
     * @param keyId      the {@code kid} header of the token status list token
     * @return the serialized token status list token
     * @throws JOSEException if the token status list token cannot be signed
     */
    public static String createInvalidTokenStatusListTokenVerifiableCredentialInvalidClaimBits(ECKey signingKey, String issuerId, String keyId) throws JOSEException {
        var claims = JWT_CLAIM_SET_BUILDER
                .issuer(issuerId)
                .claim("status_list", new JWTClaimsSet.Builder()
                        .claim("bits", "NEITHER_OF_1_2_4_OR_8")
                        //.claim("lst", statusList)
                        .issueTime(new Date())
                        .build()
                        .toJSONObject()).build();
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("statuslist+jwt"))
                .keyID(keyId)
                .build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    /**
     * Creates a token status list token without the {@code status_list.lst} claim.
     *
     * @param signingKey the key used to sign the token status list token
     * @param issuerId   the {@code iss} claim of the token status list token
     * @param keyId      the {@code kid} header of the token status list token
     * @return the serialized token status list token
     * @throws JOSEException if the token status list token cannot be signed
     */
    public static String createInvalidTokenStatusListTokenVerifiableCredentialMissingClaimLst(ECKey signingKey, String issuerId, String keyId) throws JOSEException {
        var claims = JWT_CLAIM_SET_BUILDER
                .issuer(issuerId)
                .claim("status_list", new JWTClaimsSet.Builder()
                        .claim("bits", 2)
                        .issueTime(new Date())
                        .build()
                        .toJSONObject()).build();
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("statuslist+jwt"))
                .keyID(keyId)
                .build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    /**
     * Stubs {@link StatusListResolver#resolveStatusList(String)} to serve a token status list for
     * {@link #SPEC_SUBJECT}, built from the given status list and signed with the given key and issuer.
     *
     * @param mockedStatusListResolver the mocked resolver to stub
     * @param statusList               the encoded status list, used as {@code lst} claim
     * @param signingKey               the key used to sign the token status list token
     * @param issuerId                 the {@code iss} claim of the token status list token
     * @param keyId                    the {@code kid} header of the token status list token
     * @throws JOSEException if the token status list token cannot be signed
     */
    public static void stubStatusListResolver(StatusListResolver mockedStatusListResolver, String statusList, ECKey signingKey, String issuerId, String keyId) throws JOSEException {
        when(mockedStatusListResolver.resolveStatusList(SPEC_SUBJECT))
                .thenAnswer(invocation -> createTokenStatusListTokenVerifiableCredential(
                        statusList,
                        signingKey,
                        issuerId,
                        keyId)
                );
    }

    /**
     * Stubs {@link StatusListResolver#resolveStatusList(String)} to serve the example status list
     * {@link #SPEC_STATUS_LIST}, signed with the issuer key and key id of the given credential mock.
     *
     * @param mockedStatusListResolver the mocked resolver to stub
     * @param mock                     the credential mock providing the signing key, issuer id and key id
     */
    public static void stubStatusListResolver(StatusListResolver mockedStatusListResolver, SDJWTCredentialMock mock) {
        when(mockedStatusListResolver.resolveStatusList(SPEC_SUBJECT))
                .thenAnswer(invocation -> createTokenStatusListTokenVerifiableCredential(
                        SPEC_STATUS_LIST,
                        mock.getKey(),
                        mock.getIssuerId(),
                        mock.getKidHeaderValue())
                );
    }
}
