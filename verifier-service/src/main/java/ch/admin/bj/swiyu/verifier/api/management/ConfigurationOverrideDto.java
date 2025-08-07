package ch.admin.bj.swiyu.verifier.api.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(description = "Override for Verifier configuration to be use for this one verification")
public record ConfigurationOverrideDto(
    @Schema(description = "Override for the EXTERNAL_URL - the url the wallet should call, to fetch the request object and send the verification response to.")
    @Nullable
    @JsonProperty("external_url")
    String externalUrl,
    @Schema(description = "Override to be used in clientId instead of environment variable VERIFIER_DID")
    @Nullable
    @JsonProperty("verifier_did")
    String verifierDid,
    @Schema(description = "Override for DID_VERIFICATION_METHOD, the id of the public key in the did document. Most often the full did and a unique id after #")
    @Nullable
    @JsonProperty("verification_method")
    String verificationMethod,
    @Schema(description = "ID of the key in the HSM")
    @Nullable
    @JsonProperty("key_id")
    String keyId,
    @Schema(description = "The pin which protects the key in the hsm, if any. Note that this only the key pin, not hsm password or partition pin.")
    @Nullable
    @JsonProperty("key_pin")
    String keyPin
) {
}
