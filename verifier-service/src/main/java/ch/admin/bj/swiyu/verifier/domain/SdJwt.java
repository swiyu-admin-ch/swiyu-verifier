package ch.admin.bj.swiyu.verifier.domain;

import com.authlete.sd.Disclosure;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SdJwt {
    /**
     * Unprocessed sd-jwt
     */
    private final String rawSdJwt;
    private String[] parts;
    @Setter
    private JWSHeader header;
    @Setter
    private JWTClaimsSet claims;

    public SdJwt(String rawSdJwt) {
        this.rawSdJwt = rawSdJwt;
    }

    public String getJwt() {
        return getParts()[0];
    }

    private String[] getParts() {
        if(parts == null){
            parts = rawSdJwt.split("~");
        }
        return parts;
    }

    public boolean hasKeyBinding() {
        return !rawSdJwt.endsWith("~");
    }

    public Optional<String> getKeyBinding() {
        if(hasKeyBinding()){
            return Optional.of(getParts()[getParts().length-1]);
        }
        return Optional.empty();
    }

    public List<Disclosure> getDisclosures() {
        int disclosureLength = getParts().length;
        if(hasKeyBinding()){
            // Last entry in parts is key binding
            disclosureLength -= 1;
        }
        return Arrays.stream(Arrays.copyOfRange(getParts(), 1, disclosureLength))
                .map(Disclosure::parse).toList();
    }

    public String getPresentation() {
        return rawSdJwt.substring(0, rawSdJwt.lastIndexOf("~")+1);
    }

    public JWSHeader getHeader() {
        if(header == null){
            throw new IllegalStateException("header has not yet been verified");
        }
        return header;
    }

    public JWTClaimsSet getClaims() {
        if (claims == null){
            throw new IllegalStateException("claims has not yet been verified");
        }
        return claims;
    }
}
