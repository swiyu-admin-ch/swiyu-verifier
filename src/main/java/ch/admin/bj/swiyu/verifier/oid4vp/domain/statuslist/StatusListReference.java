package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.IssuerPublicKeyLoader;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.util.Map;


/**
 * Pointer towards a status list on a registry which can be resolved and verified
 */
@Data
@Slf4j
@AllArgsConstructor
public abstract class StatusListReference {

    private final StatusListResolverAdapter statusListResolverAdapter;
    /**
     * Example for Token Status List
     * <pre>
     * <code>
     *  {
     *      "idx": 0,
     *      "uri": "https://example.com/statuslists/1"
     *  }
     * </code>
     * </pre>
     * <p>
     * or for BitString Status List
     * <pre>
     * <code>
     * {
     *  "id": "https://example.com/credentials/status/4#23452",
     *  "type": "BitstringStatusListEntry",
     *  "statusPurpose": "suspension",
     *  "statusListIndex": "23452",
     *  "statusListCredential": "https://example.com/credentials/status/4"
     * }
     * </code>
     * </pre>
     */
    private final Map<String, Object> statusListReferenceClaims;

    private final IssuerPublicKeyLoader issuerPublicKeyLoader;

    /**
     * Verifies the status of the index pointed to by the status list reference
     * Should the verification fail, a runtime exception is thrown
     */
    public abstract void verifyStatus();

    protected abstract String getStatusListRegistryUri();

    /**
     * Validates the status list vc including the signature
     * <p>
     * This is the shape of for TokenStatusList
     * <pre><code>
     *    {
     *        "iss": "did:tdw:gnsgindemrqwgmzzgjrgcntgg42tknjtgizgkztbmrsdcnbzgm3dcn3gmnrgcnjwhftdimbthfstmnjrgbqwmmbthayteyjxme2wgoi=:identifier-data-service-d.bit.admin.ch:api:v1:did:c84eebdd-4417-4d88-bb5e-f0ce0ee38844",
     *        "sub": "https://status-data-service-d.apps.p-szb-ros-shrd-npr-01.cloud.admin.ch/api/v1/statuslist/05d2e09f-21dc-4699-878f-89a8a2222c67.jwt",
     *        "iat": 1726829581,
     *        "status_list": {
     *            "bits": 2,
     *            "lst": "eNpjYBjRAAAA_wAB"
     *        }
     *    }
     * </code></pre>
     *
     * @return
     * @throws ParseException
     */
    protected Map<String, Object> getStatusListVC() throws ParseException {
        var uri = getStatusListRegistryUri();
        try {
            var vc = getStatusListResolverAdapter().resolveStatusList(uri);
            var signedVC = SignedJWT.parse(vc);
            verifyJWT(signedVC);
            return signedVC.getJWTClaimsSet().getClaims();
        } catch (VerificationException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Could not retrieve status list vc.", e);
            throw statusListError(String.format("Could not retrieve status list vc from %s", uri));
        } catch (ParseException e) {
            log.warn("Statuslist could not be parsed as JWT.", e);
            throw statusListError(String.format("Statuslist could not be parsed as JWT. %s", uri));
        }
    }

    protected abstract void verifyJWT(SignedJWT vc);

    protected VerificationException statusListError(String errorText) {
        return VerificationException.credentialError(
                VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST,
                errorText);
    }

    protected VerificationException statusListError(String errorText, Throwable cause) {
        return VerificationException.credentialError(
                cause,
                VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST,
                errorText);
    }
}
