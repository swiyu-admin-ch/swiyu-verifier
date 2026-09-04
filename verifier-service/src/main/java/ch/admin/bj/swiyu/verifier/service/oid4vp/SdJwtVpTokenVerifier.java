package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwt;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtParser;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtVcValidator;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtParseException;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtVerificationException;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.dto.StatusVerificationResultDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListMapper;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListReferenceDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListCacheService;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.*;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

/**
 * Verifies SD-JWT trust statements (which are themselves VP tokens) using the
 * same core verification logic as regular VP tokens, but with trust-specific
 * semantics.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SdJwtVpTokenVerifier {

    private final DidResolverFacade didResolver;
    private final DidJwtValidator jwtValidator;
    private final StatusListCacheService statusListCacheService;
    private final ApplicationProperties applicationProperties;
    private final VerificationProperties verificationProperties;
    private final TokenStatusListVerifier statusListVerifier;
    @Deprecated(since = "Trust Protocol 2.0")
    private final DidKidParser didKidParser = new DidKidParser();

    @Deprecated(since = "Trust Protocol 2.0")
    public SdJwt verifyVpTokenTrustStatement(String vpToken, Management management) {

        try {
            SdJwtVcValidator validator = new SdJwtVcValidator(jwtValidator);

            SdJwt sdJwt;
            String headerKid;

            sdJwt = SdJwtParser.parseSdJwt(vpToken);

            validator.validateAndSetHeader(sdJwt);

            headerKid = sdJwt.getHeader().getKeyID();
            var publicKey = didResolver.resolveKey(headerKid);

            validator.validateAndSetJwt(sdJwt, publicKey);

            // For Trust Protocol 1.0 the KID and DID must match
            var didFromKid = didKidParser.getDidFromAbsoluteKid(headerKid);
            if (didFromKid == null || !didFromKid.equals(sdJwt.getClaims().getIssuer())) {
                throw credentialError(CREDENTIAL_INVALID, "Trust Statements 1.0 MUST have correlating and iss claims");
            }

            validateKeyBinding(sdJwt, canHaveKeyBinding(sdJwt.getClaims()), management, validator);

            verifyStatus(sdJwt.getClaims().getClaims(), sdJwt.getHeader());

            // Resolve Disclosures
            validator.processDisclosures(sdJwt);

            return sdJwt;
        } catch (SdJwtParseException e) {
            log.error("Failed to parse VP token: {}", e.getMessage(), e);
            throw credentialError(MALFORMED_CREDENTIAL, e.getMessage());
        } catch (SdJwtVerificationException e) {
            log.error("Verification failed for VP token: {}", e.getMessage(), e);
            throw credentialError(MALFORMED_CREDENTIAL, e.getMessage());
        }
    }

    protected Optional<StatusVerificationResultDto> verifyStatus(Map<String, Object> vcClaims, JWSHeader header) {
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

    /**
     * Validate the holder (key) binding for an SD-JWT that represents a Verifiable Presentation (VP) token.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>If cryptographic holder-binding is not required and the token contains no key binding, return silently.</li>
     *   <li>If cryptographic holder-binding is required but the token lacks a key binding, throw a
     *       credential error with code HOLDER_BINDING_MISMATCH.</li>
     *   <li>Otherwise, validate the holder-binding</li>
     * </ul>
     *
     * @param sdJwt                                the parsed SD-JWT {@link SdJwt} containing the VP token to validate
     * @param isCryptographicHolderBindingRequired boolean whether cryptographic holder-binding is mandatory
     * @param management                           the Management {@link Management} provides configuration override and request nonce
     * @param validator                            the SD-JWT VC validator used to perform the low-level key-binding checks
     */
    void validateKeyBinding(SdJwt sdJwt, boolean isCryptographicHolderBindingRequired, Management management, SdJwtVcValidator validator) {

        if (!isCryptographicHolderBindingRequired && !sdJwt.hasKeyBinding()) {
            return;
        }

        if (isCryptographicHolderBindingRequired && !sdJwt.hasKeyBinding()) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }

        var configurationOverride = management.getConfigurationOverride();
        var expectedAudience = configurationOverride.verifierDidOrDefaultWithPrefix(applicationProperties);
        var requestNonce = management.getRequestNonce();

        try {
            validator.validateKeyBinding(sdJwt,
                    expectedAudience,
                    requestNonce,
                    verificationProperties.getAcceptableProofTimeWindowSeconds());

        } catch (SdJwtVerificationException e) {
            log.error("Failed to validate key binding for VP token: {}", e.getMessage(), e);
            throw credentialError(e, HOLDER_BINDING_MISMATCH, e.getMessage());
        }
    }

    /**
     *
     * @param claims the claims of a VP Token
     * @return true, if the VP Token is set up to have a key binding
     */
    boolean canHaveKeyBinding(JWTClaimsSet claims) {
        return claims.getClaims().containsKey("cnf");
    }
}