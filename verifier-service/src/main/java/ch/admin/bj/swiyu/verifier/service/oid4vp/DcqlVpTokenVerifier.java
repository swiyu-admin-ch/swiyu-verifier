package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwt;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtParser;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtVcValidator;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtParseException;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtVerificationException;
import ch.admin.bj.swiyu.statuslist.dto.StatusVerificationResultDto;
import ch.admin.bj.swiyu.verifier.domain.IssuerTrustMarker;
import ch.admin.bj.swiyu.verifier.domain.SdJwtVerificationResult;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Optional;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.MALFORMED_CREDENTIAL;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

@Service
@Slf4j
@RequiredArgsConstructor
public class DcqlVpTokenVerifier {

    private final SdJwtVpTokenVerifier sdJwtVpTokenVerifier;
    private final IssuerTrustValidator issuerTrustValidator;
    private final DidJwtValidator jwtValidator;
    private final DidResolverFacade didResolver;
    private final DidKidParser didKidParser = new DidKidParser();

    public SdJwtVerificationResult verifyVpTokenForDCQLRequest(String vpToken, Management management, DcqlCredential dcqlCredential) {

        try {
            SdJwtVcValidator validator = new SdJwtVcValidator(jwtValidator);

            SdJwt sdJwt;
            String headerKid;

            sdJwt = SdJwtParser.parseSdJwt(vpToken);

            // also checks header typ -> typ does not need to match dcqlCredential.format, but it must be a valid SD-JWT type `vc+sd-jwt` or `dc+sd-jwt` for backward compatibility.
            validator.validateAndSetHeader(sdJwt);

            headerKid = sdJwt.getHeader().getKeyID();
            var publicKey = didResolver.resolveKey(headerKid);

            validator.validateAndSetJwt(sdJwt, publicKey);

            // require_cryptographic_holder_binding default is true therefore if not set to false it will be treated as true
            boolean cryptographicHolderBindingRequired = Boolean.TRUE.equals(dcqlCredential.getRequireCryptographicHolderBinding()) || dcqlCredential.getRequireCryptographicHolderBinding() == null;
            sdJwtVpTokenVerifier.validateKeyBinding(sdJwt, cryptographicHolderBindingRequired, management, validator);

            // Perform issuer trust validation based on claims
            JWTClaimsSet claims = sdJwt.getClaims();
            IssuerTrustMarker trustMarkers;
            try {
                var issuerDID = didKidParser.getDidFromAbsoluteKid(headerKid);
                trustMarkers = issuerTrustValidator.validateTrust(issuerDID, claims.getStringClaim("vct"), management);
            } catch (ParseException e) {
                log.error("Failed to extract vct claim from JWT token", e);
                throw credentialError(MALFORMED_CREDENTIAL, "Failed to extract information from JWT token");
            }

            Optional<StatusVerificationResultDto> statusVerificationResult = sdJwtVpTokenVerifier.verifyStatus(sdJwt.getClaims().getClaims(), sdJwt.getHeader());

            // Resolve Disclosures
            validator.processDisclosures(sdJwt);

            return new SdJwtVerificationResult(sdJwt, trustMarkers, statusVerificationResult);
        } catch (SdJwtParseException e) {
            log.error("Failed to parse VP token: {}", e.getMessage(), e);
            throw credentialError(MALFORMED_CREDENTIAL, e.getMessage());
        } catch (SdJwtVerificationException e) {
            log.error("Verification failed for VP token: {}", e.getMessage(), e);
            throw credentialError(MALFORMED_CREDENTIAL, e.getMessage());
        }
    }
}
