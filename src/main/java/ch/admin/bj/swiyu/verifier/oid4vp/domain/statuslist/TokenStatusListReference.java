/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.common.json.JsonUtil;
import ch.admin.bj.swiyu.verifier.oid4vp.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.oid4vp.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bj.swiyu.verifier.oid4vp.service.statuslist.StatusListResolverAdapter;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST;

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


    public TokenStatusListReference(StatusListResolverAdapter adapter, Map<String, Object> statusListReferenceClaims, IssuerPublicKeyLoader issuerPublicKeyLoader, String referencedTokenIssuer, int maxBufferSize) {
        super(adapter, statusListReferenceClaims, issuerPublicKeyLoader, referencedTokenIssuer, maxBufferSize);
    }


    @Override
    public void verifyStatus() {
        try {
            Map<String, Object> statusListVC = getStatusListVC();
            log.trace("Begin unpacking Status List");
            Map<String, Object> statusListData = JsonUtil.getJsonObject(statusListVC.get("status_list"));
            int statusListBits = Integer.parseInt(statusListData.get("bits").toString());
            String zippedStatusList = (String) statusListData.get("lst");
            TokenStatusListToken statusList = TokenStatusListToken.loadTokenStatusListToken(statusListBits, zippedStatusList, getMaxBufferSize());
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
            throw VerificationException.credentialError(e, UNRESOLVABLE_STATUS_LIST, "The VC cannot be validated as the remote list does not contain this VC!");
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
            // See https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens#framework
            new DefaultJWTClaimsVerifier<>(
                    // Validate that the issuer of the VC is the same as the issuer of referenced token
                    new JWTClaimsSet.Builder().issuer(getReferencedTokenIssuer()).build(),
                    Set.of("iss")
            ).verify(vc.getJWTClaimsSet(), null);
            var issuer = vc.getJWTClaimsSet().getIssuer();
            var publicKey = getIssuerPublicKeyLoader().loadPublicKey(issuer, vc.getHeader().getKeyID());
            if (!vc.verify(toJwsVerifier(publicKey))) {
                throw statusListError("Failed to verify JWT: Issuer public key does not match signature!");
            }
        } catch (LoadingPublicKeyOfIssuerFailedException | ParseException | JOSEException |
                 IllegalArgumentException e) {
            throw statusListError("Failed to verify JWT: Could not verify against issuer public key", e);
        } catch (BadJWTException e) {
            throw statusListError(String.format("Failed to verify JWT: Invalid JWT token. %s", e.getMessage()), e);
        }
    }

    private static JWSVerifier toJwsVerifier(PublicKey publicKey) throws JOSEException {
        if (publicKey instanceof ECPublicKey) {
            return new ECDSAVerifier((ECPublicKey) publicKey);
        }
        throw new IllegalArgumentException("Unsupported public key type: " + publicKey.getClass().getName());
    }

}