/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.experimental.UtilityClass;

import java.util.Date;

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
     * status[3] = 3 - revoked & suspended
     * </code></pre>
     */
    public static final String SPEC_STATUS_LIST = "eNo76fITAAPfAgc";
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
}
