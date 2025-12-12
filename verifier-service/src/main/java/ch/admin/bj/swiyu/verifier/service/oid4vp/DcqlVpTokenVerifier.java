package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.ParseException;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.MALFORMED_CREDENTIAL;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

@Service
@RequiredArgsConstructor
public class DcqlVpTokenVerifier {

    private final SdJwtVpTokenVerifier sdJwtVpTokenVerifier;
    private final IssuerTrustValidator issuerTrustValidator; // new dependency for issuer trust


    public SdJwt verifyVpTokenForDCQLRequest(SdJwt vpToken, Management management, DcqlCredential dcqlCredential) {
        // Validate Basic JWT (header, times, signature)
        sdJwtVpTokenVerifier.verifyVerifiableCredentialJWT(vpToken, management);

        // Perform issuer trust validation based on claims
        JWTClaimsSet claims = vpToken.getClaims();
        try {
            issuerTrustValidator.validateTrust(claims.getIssuer(), claims.getStringClaim("vct"), management);
        } catch (ParseException e) {
            throw credentialError(MALFORMED_CREDENTIAL, "Failed to extract information from JWT token");
        }

        // If Key Binding is present, validate that it is correct
        var requireKeyBinding = Boolean.TRUE.equals(dcqlCredential.getRequireCryptographicHolderBinding());

        if (vpToken.hasKeyBinding()) {
            sdJwtVpTokenVerifier.validateKeyBinding(vpToken, management);
        } else if (requireKeyBinding || sdJwtVpTokenVerifier.requiresKeyBinding(vpToken.getClaims())) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }
        sdJwtVpTokenVerifier.verifyStatus(vpToken.getClaims().getClaims(), management);

        // Resolve Disclosures
        sdJwtVpTokenVerifier.validateDisclosures(vpToken, management);

        return vpToken;
    }

}
