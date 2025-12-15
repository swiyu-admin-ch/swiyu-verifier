package ch.admin.bj.swiyu.verifier.domain;

import ch.admin.bj.swiyu.verifier.common.base64.Base64Utils;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import com.authlete.sd.Disclosure;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The class models a selective disclosure JSON Web Token (SD-JWT) w.r.t.
 * <a href="https://www.rfc-editor.org/rfc/rfc9901.html#section-4">RFC 9901 ("Selective Disclosure for JSON Web Tokens")</a>
 */
public class SdJwt {

    /**
     * The compact serialized format for the SD-JWT is the concatenation of each part delineated with a single tilde ('~') character.
     */
    final static public String JWT_PART_DELINEATION_CHARACTER = "~";

    @Getter
    private final String[] parts;
    @Setter
    private JWSHeader header;
    @Setter
    private JWTClaimsSet claims;

    /*
     * <a href="https://www.rfc-editor.org/rfc/rfc9901.html#name-disclosures">Disclosures (zero or more)</a>
     */
    /*
    @Getter
    private final List<Disclosure> disclosures;
     */

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9901.html#section-4.3">Key Binding JWT</a>
     */
    @Getter
    private final Optional<String> keyBinding;

    @Getter
    private final String presentation;

    @Getter
    private final String presentationHash;

    /**
     * The only constructor for the class. The class instantiation is made as outcome of parsing the supplied string
     * representing a <a href="https://www.rfc-editor.org/rfc/rfc9901.html">SD-JWT</a>.
     *
     * @param rawSdJwt to parse
     */
    public SdJwt(String rawSdJwt) {

        if (!rawSdJwt.contains(JWT_PART_DELINEATION_CHARACTER)
                || rawSdJwt.contains(JWT_PART_DELINEATION_CHARACTER + JWT_PART_DELINEATION_CHARACTER) // denotes multiple tilde ('~') characters
        ) {
            throw VerificationException.credentialError(
                    VerificationErrorResponseCode.MALFORMED_CREDENTIAL,
                    "The compact serialized format for the SD-JWT is the concatenation of each part delineated with a single tilde ('~') character");
        }

        // According to https://www.rfc-editor.org/rfc/rfc9901.html#section-4:
        // "The compact serialized format for the SD-JWT is the concatenation of each part delineated with a single tilde ('~') character"

        // CAUTION The String#split method ignores trailing delimiters unless a limit of -1 is explicitly specified:
        //         "If the limit is negative then the pattern will be applied as many times as possible and the array can have any length."
        this.parts = rawSdJwt.split(JWT_PART_DELINEATION_CHARACTER);

        // According to https://www.rfc-editor.org/rfc/rfc9901.html#section-4:
        // "In the case that there is no Key Binding JWT, the last element MUST be an empty string and the last separating tilde character MUST NOT be omitted."
        if (!rawSdJwt.endsWith(JWT_PART_DELINEATION_CHARACTER)) {
            this.keyBinding = Optional.of(parts[parts.length - 1]);
        } else {
            this.keyBinding = Optional.empty();
        }

        this.presentation = rawSdJwt.substring(0, rawSdJwt.lastIndexOf(JWT_PART_DELINEATION_CHARACTER) + 1);

        try {
            this.presentationHash = Base64Utils.encodeBase64(
                    MessageDigest.getInstance("sha-256") // getInstance may throw NoSuchAlgorithmException
                            .digest(this.presentation.getBytes(Charset.defaultCharset())));
        } catch (NoSuchAlgorithmException exc) {
            // CAUTION No VerificationException.credentialError(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, ...)
            //         should be called here, as there must be a provider supporting a MessageDigestSpi implementation
            //         for the (standard) "sha-256" algorithm
            throw new IllegalStateException("Loading hash algorithm failed. Please check the configuration", exc);
        }
    }

    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9901.html#section-4.1">Issuer-Signed JWT</a> part of the SD-JWT.
     *
     * @return issuer-signed JWT
     */
    public String getJwt() {
        return parts[0];
    }

    public boolean hasKeyBinding() {
        return keyBinding.isPresent();
    }

    public List<Disclosure> getDisclosures() {
        int disclosureLength = getParts().length;
        if (hasKeyBinding()) {
            // Last entry in parts is key binding
            disclosureLength -= 1;
        }
        return Arrays.stream(Arrays.copyOfRange(getParts(), 1, disclosureLength))
                .map(Disclosure::parse).toList();
    }

    public JWSHeader getHeader() {
        if (header == null) {
            throw new IllegalStateException("header has not yet been verified");
        }
        return header;
    }

    public JWTClaimsSet getClaims() {
        if (claims == null) {
            throw new IllegalStateException("claims has not yet been verified");
        }
        return claims;
    }
}
