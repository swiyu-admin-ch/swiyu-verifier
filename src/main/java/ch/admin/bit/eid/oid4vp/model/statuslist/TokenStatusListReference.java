/*
 * SPDX-FileCopyrightText: 2024 Swiss Confederation
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bit.eid.oid4vp.model.statuslist;

import ch.admin.bit.eid.oid4vp.exception.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.IssuerPublicKeyLoader;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

/**
 * Referenced Token
 * See <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-status-list-02.html#name-status-list-token-in-jwt-fo">spec</a>
 * Status List reference as written into VC
 * <pre>
 * <code>
 *     {
 *       "idx": 0,
 *       "uri": "https://example.com/statuslists/1"
 *     }
 * </code>
 * </pre>
 */
public class TokenStatusListReference extends StatusListReference {


    public TokenStatusListReference(RestClient client, Map<String, Object> statusListReferenceClaims, ManagementEntity presentationManagementEntity, IssuerPublicKeyLoader issuerPublicKeyLoader) {
        super(client, statusListReferenceClaims, presentationManagementEntity, issuerPublicKeyLoader);
    }


    @Override
    public void verifyStatus() {
        try {
            var statusListVC = getStatusListVC();
            Map<String, Object> statusListData = (Map<String, Object>) statusListVC.get("status_list");
            var statusList = TokenStatusListToken.loadTokenStatusListToken((Integer) statusListData.get("bits"), (String) statusListData.get("lst"));
            var credentialStatus = TokenStatusListBit.createStatus(statusList.getStatus((Integer) getStatusListReferenceClaims().get("idx")));

            switch (credentialStatus) {
                case TokenStatusListBit.VALID:
                    break; // All Good!
                case TokenStatusListBit.SUSPENDED:
                    throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_SUSPENDED, "Credential has been Suspended!", getPresentationManagementEntity());
                case TokenStatusListBit.REVOKED:
                    throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_REVOKED, "Credential has been Revoked!", getPresentationManagementEntity());
                default:
                    throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_REVOKED, "Unexpected VC Status!", getPresentationManagementEntity());
            }
        } catch (ParseException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.UNRESOLVABLE_STATUS_LIST, "Failed to parse the Status List VC from the status registry!", getPresentationManagementEntity());
        } catch (IOException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.UNRESOLVABLE_STATUS_LIST, "Failed to parse the Status List bits!", getPresentationManagementEntity());
        }

    }

    @Override
    public String getStatusListRegistryUri() {
        return (String) getStatusListReferenceClaims().get("uri");
    }

    @Override
    protected void verifyJWT(SignedJWT vc) {
        if (!"statuslist+jwt".equals(vc.getHeader().getType().toJSONString())) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.UNRESOLVABLE_STATUS_LIST, "Status List is not of type statuslist+jwt", getPresentationManagementEntity());
        }
        try {
            var publicKey = getIssuerPublicKeyLoader().loadPublicKey(vc.getJWTClaimsSet().getIssuer(), vc.getHeader().getKeyID());
            // TODO EID-1673: ask Georg to Refactor issuer key loader & make the VC verification callable
        } catch (LoadingPublicKeyOfIssuerFailedException | ParseException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.UNRESOLVABLE_STATUS_LIST, "Failed to get Status List VC Key", getPresentationManagementEntity());
        }
    }
}
