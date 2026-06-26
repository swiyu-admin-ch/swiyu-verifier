package ch.admin.bj.swiyu.verifier.domain.ecosystem;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The API which is provided by the token provider of the swiyu ecosystem api
 * components.
 */
public interface TokenApi {

    @PostExchange(contentType = "application/x-www-form-urlencoded")
    TokenResponse getNewToken(
            @RequestParam(required = false) String refresh_token,
            @RequestParam String grant_type);

    record TokenResponse(
            String access_token,
            String refresh_token) {
    }

}
