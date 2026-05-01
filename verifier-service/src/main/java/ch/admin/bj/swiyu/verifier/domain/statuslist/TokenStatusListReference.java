package ch.admin.bj.swiyu.verifier.domain.statuslist;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.common.util.json.JsonUtil;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
class TokenStatusListReference extends StatusListReference {


    public TokenStatusListReference(StatusListResolverAdapter adapter, Map<String, Object> statusListReferenceClaims, DidJwtValidator didJwtValidator, DidResolverFacade didResolverFacade, String referencedTokenIssuer, int maxBufferSize) {
        super(adapter, statusListReferenceClaims, didJwtValidator, didResolverFacade, referencedTokenIssuer, maxBufferSize);
    }

    /**
     * A helper in charge of extracting <a href="https://drafts.oauth.net/draft-ietf-oauth-status-list/draft-ietf-oauth-status-list.html#section-4.2">bits</a> claim
     * from a supplied <a href="https://drafts.oauth.net/draft-ietf-oauth-status-list/draft-ietf-oauth-status-list.html#section-5.1">"status_list"</a> map:
     * <pre>
     *     bits: REQUIRED. JSON Integer specifying the number of bits per Referenced Token in the compressed byte array (lst). The allowed values for bits are 1,2,4 and 8.
     * </pre>
     *
     * @param statusListVC to extract "bits" claim from
     * @return "bits" claim
     */
    private static int getBitsClaim(Map<String, Object> statusListVC) {
        final Object bitsClaim = JsonUtil.getJsonObject(statusListVC.get("status_list")).get("bits");
        if (bitsClaim == null) {
            throw VerificationException.credentialError(VerificationErrorResponseCode.INVALID_TOKEN_STATUS_LIST, "Missing REQUIRED claim 'bits'");
        }
        final String bitsClaimStr = bitsClaim.toString();
        if (bitsClaimStr.isBlank() || !bitsClaimStr.matches("[1248]")) {
            throw VerificationException.credentialError(VerificationErrorResponseCode.INVALID_TOKEN_STATUS_LIST, "Invalid REQUIRED claim 'bits'");
        }
        // At this point (thanks to regex matching above), it should be 'safe' to parse the claim straight away
        return Integer.parseInt(bitsClaim.toString());
    }

    /**
     * A helper in charge of extracting <a href="https://drafts.oauth.net/draft-ietf-oauth-status-list/draft-ietf-oauth-status-list.html#section-4.2">lst</a> claim
     * from a supplied <a href="https://drafts.oauth.net/draft-ietf-oauth-status-list/draft-ietf-oauth-status-list.html#section-5.1">"status_list"</a> map:
     * <pre>
     *     lst: REQUIRED. JSON String that contains the status values for all the Referenced Tokens it conveys statuses for. The value MUST be the base64url-encoded compressed byte array as specified in Section 4.1.
     * </pre>
     *
     * @param statusListVC to extract "lst" claim from
     * @return "lst" claim
     */
    private static String getLstClaim(Map<String, Object> statusListVC) {

        final Object lstClaim = JsonUtil.getJsonObject(statusListVC.get("status_list")).get("lst");
        if (lstClaim == null) {
            throw VerificationException.credentialError(VerificationErrorResponseCode.INVALID_TOKEN_STATUS_LIST, "Missing REQUIRED claim 'lst'");
        }
        final String zippedStatusList = lstClaim.toString();
        if (zippedStatusList.isBlank()) {
            throw VerificationException.credentialError(VerificationErrorResponseCode.INVALID_TOKEN_STATUS_LIST, "Invalid REQUIRED claim 'lst'");
        }

        return zippedStatusList;
    }

    /**
     * A helper in charge of extracting <a href="https://drafts.oauth.net/draft-ietf-oauth-status-list/draft-ietf-oauth-status-list.html#section-6.2">idx</a> claim:
     * <pre>
     *     idx: REQUIRED. The idx (index) claim MUST specify a non-negative Integer that represents the index to check for status information in the Status List for the current Referenced Token.
     * </pre>
     *
     * @return "idx" claim
     */
    private int getIdxClaim() {

        var idxClaim = getStatusListReferenceClaims().get("idx");
        if (idxClaim == null) {
            throw VerificationException.credentialError(VerificationErrorResponseCode.INVALID_TOKEN_STATUS_LIST, "Missing REQUIRED claim 'idx'");
        }
        final String idxClaimStr = idxClaim.toString();
        if (idxClaimStr.isBlank() || !idxClaimStr.matches("^?\\d+$")) {
            throw VerificationException.credentialError(VerificationErrorResponseCode.INVALID_TOKEN_STATUS_LIST, "Invalid REQUIRED claim 'idx'");
        }
        // At this point (thanks to regex matching above), it should be 'safe' to parse the claim straight away
        return Integer.parseInt(idxClaimStr);
    }

    @Override
    public void verifyStatus() {
        try {
            Map<String, Object> statusListVC = getStatusListVC();
            log.trace("Begin unpacking Status List");
            TokenStatusListToken statusList = TokenStatusListToken.loadTokenStatusListToken(
                    getBitsClaim(statusListVC),
                    getLstClaim(statusListVC),
                    getMaxBufferSize()
            );
            log.trace("Unpacked Status List with length {}", statusList.getStatusList().length);

            TokenStatusListBit credentialStatus = TokenStatusListBit.createStatus(statusList.getStatus(getIdxClaim()));
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
            throw VerificationException.credentialError(e, VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST, "The VC cannot be validated as the remote list does not contain this VC!");
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
        // Step 2: validate signature via DidJwtValidator (Flow B) and verify issuer identity via kid (ADR-027)
        // The iss claim is explicitly ignored; the issuer is established from the absolute kid header.
        try {
            String jwtString = vc.serialize();
            String didUrl = getDidJwtValidator().getAndValidateResolutionUrl(jwtString);
            if (!getReferencedTokenIssuer().equals(didUrl)) {
                throw statusListError(String.format(
                        "Failed to verify JWT: Invalid JWT token. JWT iss claim value rejected: expected %s but kid resolved to %s",
                        getReferencedTokenIssuer(), didUrl));
            }
            try (var didDoc = getDidResolverFacade().resolveDid(didUrl)) {
                getDidJwtValidator().validateJwt(jwtString, didDoc);
            }
        } catch (JwtValidatorException e) {
            throw statusListError("Failed to verify JWT: Could not resolve issuer DID", e);
        } catch (Exception e) {
            if (e instanceof VerificationException ve) throw ve;
            throw statusListError("Failed to verify JWT: Could not verify against issuer public key", e);
        }
    }

}