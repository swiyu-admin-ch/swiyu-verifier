package ch.admin.bit.eid.oid4vp.model.did;

import com.nimbusds.jose.jwk.JWK;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.text.ParseException;
import java.util.Base64;

@Getter
public class DidJwk {
    private final String did;

    public DidJwk(@NotNull String did) {
        this.did = did;
    }

    public boolean isValid() {
        return did.startsWith("did:jwk:");
    }

    public JWK toJWK() throws ParseException {
        String base64String = did.split(":")[2];
        String jwkJson = new String(Base64.getUrlDecoder().decode(base64String));
        return JWK.parse(jwkJson);
    }
}
