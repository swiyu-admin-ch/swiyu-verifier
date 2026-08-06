package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

public class SessionNonceValidator
        implements ConstraintValidator<SessionNonce, URI> {

    @Override
    public boolean isValid(URI uri, ConstraintValidatorContext context) {

        // null check must be handled by @NotNull
        if (uri == null) {
            return true;
        }

        String query = uri.getQuery();
        if (query == null) {
            return false;
        }

        var queryParts = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams();

        return queryParts.containsKey("session_nonce") && (queryParts.getFirst("session_nonce") != null && !queryParts.getFirst("session_nonce").isBlank());
    }

}
