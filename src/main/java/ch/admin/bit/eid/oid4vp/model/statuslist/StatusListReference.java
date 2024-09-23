package ch.admin.bit.eid.oid4vp.model.statuslist;

import ch.admin.bit.eid.oid4vp.model.IssuerPublicKeyLoader;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.client.RestClient;

import java.text.ParseException;
import java.util.Map;


/**
 * Pointer towards a status list on a registry which can be resolved and verified
 */
@Data
@AllArgsConstructor
public abstract class StatusListReference {
    private final RestClient client;
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

    private final ManagementEntity presentationManagementEntity;

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
        var vc = getClient().get().uri(getStatusListRegistryUri()).retrieve().body(String.class);
        var signedVC = SignedJWT.parse(vc);
        verifyJWT(signedVC);
        return signedVC.getJWTClaimsSet().getClaims();
    }

    protected abstract void verifyJWT(SignedJWT vc);
}
