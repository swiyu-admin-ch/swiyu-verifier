package ch.admin.bj.swiyu.verifier.domain.management;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/**
 * The wallet response data, only persisted during oid4vp, will be used by verifier-agent-management.
 */
// JSON-PERSISTED (ZDD): serialized to JSON in the "management" table (see Management.walletResponse).
// Keep this type backward compatible across releases: don't rename/remove fields without a migration
// path (e.g. @JsonAlias), and keep any new field optional with a default.
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
public record ResponseData(
        
        VerificationErrorResponseCode errorCode,
        String errorDescription,
        String credentialSubjectData,
        /**
         * Full VP Token as sent by the Wallet. Decrypted, if it was originally encrypted
         */
        Map<String, List<String>> vpToken
) {
}
