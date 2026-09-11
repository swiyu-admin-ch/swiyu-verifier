package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@Validated
@Data
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties {

    @NotNull
    private Map<String, String> templateReplacement;

    @NotNull
    private Integer verificationTTL;

    @NotNull
    private String externalUrl;

    @NotNull
    private String clientId;

    @Nullable
    private String clientIdPrefix;

    @NotEmpty
    private String deeplinkSchema;

    @NotNull
    private String signingKey;

    @NotNull
    private String signingKeyVerificationMethod;

    @NotNull
    private String keyManagementMethod;

    // Limits the maximum length of compressed ciphertext accepted by the application
    // to avoid excessive memory usage or potential DoS.
    @NotNull
    @Min(1)
    private Integer maxCompressedCipherTextLength;

    @NotNull
    @Min(1)
    private Integer maxDecompressedPayloadLength;

    @NotNull
    private Integer maxVcsAccepted = 1;

    private HSMProperties hsm;

    /**
     * Seconds the Request Object should be valid for (controlls expiry of the request object)
     */
    private int requestObjectTTLSeconds = 600;

    /**
     * List of accepted registry hosts, containing both status and trust hosts
     */
    private List<String> acceptedRegistryHosts;

    /**
     * Fine-grained flags controlling which audit-related data (e.g. raw vp_token, credential_subject_data,
     * credential_evaluation) is included in the management API responses sent to the business verifier.
     * All flags default to {@code false} to keep the API response minimal unless explicitly configured.
     */
    @NotNull
    private AdditionalAuditInformationProperties additionalAuditInformation = new AdditionalAuditInformationProperties();

    public String getClientIdWithPrefix() {
        if (StringUtils.isBlank(clientIdPrefix)) {
            return clientId;
        }

        return clientIdPrefix + ":" + clientId;
    }

    /**
     * Groups the individual opt-in flags for audit information that is otherwise omitted from the
     * management API response to the business verifier (see EIDOMNI-1321).
     */
    @Data
    public static class AdditionalAuditInformationProperties {

        /**
         * If {@code true}, the raw {@code vp_token} (full presentation as sent by the wallet) is included
         * in the {@code wallet_response} of the management API response.
         */
        private boolean vpTokenEnabled = false;

        /**
         * If {@code true}, the {@code credential_subject_data} (requested claims) is included in the
         * {@code wallet_response} of the management API response.
         */
        private boolean credentialSubjectDataEnabled = false;

        /**
         * If {@code true}, the {@code credential_evaluation} (per-credential trust and status verification
         * results) is included in the management API response.
         */
        private boolean credentialEvaluationEnabled = false;
    }

}