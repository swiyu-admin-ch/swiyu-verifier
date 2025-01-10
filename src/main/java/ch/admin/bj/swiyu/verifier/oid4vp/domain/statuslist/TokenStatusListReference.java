/*
 * SPDX-FileCopyrightText: 2024 Swiss Confederation
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
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
@Slf4j
class TokenStatusListReference extends StatusListReference {


    public TokenStatusListReference(StatusListResolverAdapter adapter, Map<String, Object> statusListReferenceClaims, IssuerPublicKeyLoader issuerPublicKeyLoader) {
        super(adapter, statusListReferenceClaims, issuerPublicKeyLoader);
    }


    @Override
    public void verifyStatus() {
        try {
            Map<String, Object> statusListVC = getStatusListVC();
            log.trace("Begin unpacking Status List");
            Map<String, Object> statusListData = (Map<String, Object>) statusListVC.get("status_list");
            int statusListBits = Integer.parseInt(statusListData.get("bits").toString());
            String zippedStatusList = (String) statusListData.get("lst");
            TokenStatusListToken statusList = TokenStatusListToken.loadTokenStatusListToken(statusListBits, zippedStatusList);
            log.trace("Unpacked Status List with length {}", statusList.getStatusList().length);
            int statusListIndex = Integer.parseInt(getStatusListReferenceClaims().get("idx").toString());
            TokenStatusListBit credentialStatus = TokenStatusListBit.createStatus(statusList.getStatus(statusListIndex));
            log.trace("Fetched credential status");
            switch (credentialStatus) {
                case TokenStatusListBit.VALID:
                    break; // All Good!
                case TokenStatusListBit.SUSPENDED:
                    throw VerificationException.credentialError(VerificationErrorResponseCode.CREDENTIAL_SUSPENDED, "Credential has been Suspended!");
                case TokenStatusListBit.REVOKED:
                    throw VerificationException.credentialError(VerificationErrorResponseCode.CREDENTIAL_REVOKED, "Credential has been Revoked!");
                default:
                    throw VerificationException.credentialError(VerificationErrorResponseCode.CREDENTIAL_REVOKED, "Unexpected VC Status!");
            }
        } catch (ParseException e) {
            throw statusListError("Failed to parse the Status List VC from the status registry!", e);
        } catch (IOException e) {
            throw statusListError("Failed to parse the Status List bits!", e);
        } catch (IllegalArgumentException e) {
            throw VerificationException.credentialError(e, VerificationErrorResponseCode.CREDENTIAL_REVOKED, "Unexpected VC Status!");
        } catch (IndexOutOfBoundsException e) {
            throw VerificationException.credentialError(e, VerificationErrorResponseCode.CREDENTIAL_INVALID, "The VC cannot be validated as the remote list does not contain this VC!");
        }
    }

    @Override
    public String getStatusListRegistryUri() {
        return (String) getStatusListReferenceClaims().get("uri");
    }

    @Override
    protected void verifyJWT(SignedJWT vc) {
        // Step 1: validate VC type is of type "statuslist+jwt"
        var vcType = Optional.ofNullable(vc.getHeader().getType()).orElseThrow(() ->
                statusListError("Failed to verify JWT: Status List has no type defined, but expected is statuslist+jwt")
        ).toString();
        if (!"statuslist+jwt".equals(vcType)) {
            throw statusListError(String.format("Failed to verify JWT: Status List is not of type statuslist+jwt, was instead %s", vcType));
        }
        // Step 2: validate the signature of the VC
        try {
            var issuer = vc.getJWTClaimsSet().getIssuer();
            var publicKey = getIssuerPublicKeyLoader().loadPublicKey(issuer, vc.getHeader().getKeyID());
            if (!vc.verify(toJwsVerifier(publicKey))) {
                throw statusListError("Failed to verify JWT: Issuer public key does not match signature!");
            }
        } catch (LoadingPublicKeyOfIssuerFailedException | ParseException | JOSEException |
                 IllegalArgumentException e) {
            throw statusListError("Failed to verify JWT: Could not verify against issuer public key", e);
        }
    }

    private static JWSVerifier toJwsVerifier(PublicKey publicKey) throws JOSEException {
        if (publicKey instanceof ECPublicKey) {
            return new ECDSAVerifier((ECPublicKey) publicKey);
        }
        throw new IllegalArgumentException("Unsupported public key type: " + publicKey.getClass().getName());
    }

}
