package ch.admin.bj.swiyu.verifier.service.statuslist;

import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.dto.StatusVerificationResultDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListMapper;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListReferenceDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import com.nimbusds.jose.JWSHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.*;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

/**
 * Verifies the Status of a Verifiable Credential
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatusListVerificationService {
    private final StatusListCacheService statusListCacheService;
    private final TokenStatusListVerifier statusListVerifier;

    /**
     * If the provided claims contain a token status list reference, resovles the reference to a Token Status List
     * and validates the state of the VC
     * @param vcClaims the claims of a VC, containing the optional token status list reference
     * @param header JWS Header of the VC
     * @return Optionally the Status Verification result or empty if no token status list reference was found in the claims
     * @throws VerificationException if the token status list is malformed / exceeding size limitations
     */
    public Optional<StatusVerificationResultDto> verifyStatus(Map<String, Object> vcClaims, JWSHeader header) {
        TokenStatusListReferenceDto reference = TokenStatusListMapper.toTokenStatusListReference(vcClaims, header);
        if (reference.getStatus() == null) {
            // no Status Reference -> VC has no Status
            return Optional.empty();
        }
        try {
            TokenStatusListTokenDto statusList = statusListCacheService.getTokenStatusListTokenByUri(reference.getReferencedStatusListUri());
            if (statusList == null) {
                throw credentialError(UNRESOLVABLE_STATUS_LIST, "Status List not found or malformed");
            }
            StatusVerificationResultDto statusListState = statusListVerifier.verifyStatus(reference, statusList);
            return Optional.of(statusListState);
        } catch (
                IndexOutOfBoundsException |
                IOException |
                JwtValidatorException e) {
            throw credentialError(e, UNRESOLVABLE_STATUS_LIST, "Status List Token malformed");
        } catch (StatusListMaxSizeExceededException e) {
            throw credentialError(e, UNRESOLVABLE_STATUS_LIST, "Status list size from %s exceeds maximum allowed size".formatted(reference.getReferencedStatusListUri()));
        }
    }
}