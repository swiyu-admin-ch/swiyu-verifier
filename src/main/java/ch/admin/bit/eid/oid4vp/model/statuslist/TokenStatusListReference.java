/*
 * SPDX-FileCopyrightText: 2024 Swiss Confederation
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bit.eid.oid4vp.model.statuslist;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.IssuerPublicKeyLoader;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.Optional;

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


    public TokenStatusListReference(StatusListResolverAdapter adapter, Map<String, Object> statusListReferenceClaims, ManagementEntity presentationManagementEntity, IssuerPublicKeyLoader issuerPublicKeyLoader) {
        super(adapter, statusListReferenceClaims, presentationManagementEntity, issuerPublicKeyLoader);
    }


    @Override
    public void verifyStatus() {
        try {
            Map<String, Object> statusListVC = getStatusListVC();
            Map<String, Object> statusListData = (Map<String, Object>) statusListVC.get("status_list");
            int statusListBits = Integer.parseInt(statusListData.get("bits").toString()); // TODO Ask a java dev how we get the value, this is sometimes long and sometimes int...
            String zippedStatusList = (String) statusListData.get("lst");
            TokenStatusListToken statusList = TokenStatusListToken.loadTokenStatusListToken(statusListBits, zippedStatusList);
            int statusListIndex = Integer.parseInt(getStatusListReferenceClaims().get("idx").toString()); // TODO Ask a java dev how we get the value, this is sometimes long and sometimes int...
            TokenStatusListBit credentialStatus = TokenStatusListBit.createStatus(statusList.getStatus(statusListIndex));

            switch (credentialStatus) {
                case TokenStatusListBit.VALID:
                    break; // All Good!
                case TokenStatusListBit.SUSPENDED:
                    throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_SUSPENDED, "Credential has been Suspended!", getPresentationManagementEntity());
                case TokenStatusListBit.REVOKED:
                    throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_REVOKED, "Credential has been Revoked!", getPresentationManagementEntity());
                default:
                    // This occurs if the issuer defined more status bits than we are equipped to handle
                    throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_REVOKED, "Unexpected VC Status!", getPresentationManagementEntity());
            }
        } catch (ParseException e) {
            throw statusListError("Failed to parse the Status List VC from the status registry!");
        } catch (IOException e) {
            throw statusListError("Failed to parse the Status List bits!");
        }

    }

    @Override
    public String getStatusListRegistryUri() {
        return (String) getStatusListReferenceClaims().get("uri");
    }

    @Override
    protected void verifyJWT(SignedJWT vc) {
        var vcType = Optional.ofNullable(vc.getHeader().getType()).orElseThrow(() ->
                statusListError("Status List has no type defined, but expected is statuslist+jwt")
        ).toString();
        if (!"statuslist+jwt".equals(vcType)) {
            throw statusListError(String.format("Status List is not of type statuslist+jwt, was instead %s", vcType));
        }
//        try {
        // This does not work?
//            var publicKey = getIssuerPublicKeyLoader().loadPublicKey(vc.getJWTClaimsSet().getIssuer(), vc.getHeader().getKeyID());
        // TODO EID-1673: ask Georg to Refactor issuer key loader & make the VC verification callable
//        } catch (LoadingPublicKeyOfIssuerFailedException | ParseException e) {
//            throw statusListError("Failed to get Status List VC Key");
//        }
    }

}
