package ch.admin.bj.swiyu.verifier.dto.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

import java.util.Map;

@Schema(description = "Override for Verifier configuration to be use for this one verification")
public record ConfigurationOverrideDto(
        @Schema(description = "Override for the EXTERNAL_URL - the url the wallet should call, to fetch the request object and send the verification response to.",
                example = "https://www.example.com/verifier")
        @Nullable
        @JsonProperty("external_url")
        @Pattern(regexp = "^https://.*", message = "External URL must utilize https")
        @URL
        String externalUrl,
        @Schema(description = "Override to be used in clientId instead of environment variable VERIFIER_DID",
                example = "did:webvh:mySCID12345213:identifier-reg.trust-infra.swiyu.admin.ch:api:v1:did:00000000-0000-0000-0000-000000000000")
        @Nullable
        @JsonProperty("verifier_did")
        String verifierDid,
        @Schema(description = "Override for DID_VERIFICATION_METHOD, the id of the public key in the did document. Most often the full did and a unique id after #",
                example = "did:webvh:mySCID12345213:identifier-reg.trust-infra.swiyu.admin.ch:api:v1:did:00000000-0000-0000-0000-000000000000#myVerificationMethod-xx")
        @Nullable
        @JsonProperty("verification_method")
        String verificationMethod,
        @Schema(description = "ID of the key in the HSM",
                example = "myOverrideHSMKeyId01")
        @Nullable
        @JsonProperty("key_id")
        String keyId,
        @Schema(description = "The pin which protects the key in the hsm, if any. Note that this only the key pin, not hsm password or partition pin.",
                example = "11111")
        @Nullable
        @JsonProperty("key_pin")
        String keyPin,
        @Schema(description = """
                Optional overrides for individual fields of the client_metadata embedded in the signed
                authorization request JWT. Keys follow the OID4VP client_metadata naming conventions,
                including locale-tagged variants (e.g. "client_name#en", "client_name#de-CH",
                "logo_uri", "client_id"). Values present here take precedence over the values read
                from the configured client-metadata-file.
                """,
                example = """
                {

                  "client_name": "My Client Name",
                  "client_name#en": "My Client Name",
                  "client_name#de-CH": "Mein Kundenname",
                  "logo_uri": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVQI12NgAAIABQAABjE+ibYAAAAASUVORK5CYII="
                }
                """)
        @Nullable
        @JsonProperty("client_metadata")
        Map<String, Object> clientMetadata
) {
}
