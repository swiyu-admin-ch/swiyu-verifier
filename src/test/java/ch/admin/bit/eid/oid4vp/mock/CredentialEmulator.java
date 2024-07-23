package ch.admin.bit.eid.oid4vp.mock;


import ch.admin.bit.eid.oid4vp.model.dto.Descriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import uniffi.api.KeyPair;
import uniffi.cryptosuite.BbsCryptoSuite;
import uniffi.cryptosuite.CryptoSuiteOptions;
import uniffi.cryptosuite.CryptoSuiteType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class CredentialEmulator {
    private final BbsCryptoSuite cryptoSuite;

    public static final String ExampleJson = "{\"issuer\":\"did:example:12345\", \"type\": [\"VerifiableCredential\", \"ExampleCredential\"], \"credentialSubject\": {\"hello\":\"world\"}}";

    public CredentialEmulator() {
        this.cryptoSuite = new BbsCryptoSuite(new KeyPair());
    }

    public String createVC(List<String> requiredFields, String vcCredentialSubjectDataJson) {
        CryptoSuiteOptions options = new CryptoSuiteOptions(
                requiredFields,
                CryptoSuiteType.BBS2023,
                "did:example:12345");
        return this.cryptoSuite.addProof(vcCredentialSubjectDataJson, options);
    }

    public PresentationSubmission getCredentialSubmission() {
        Descriptor descriptor = Descriptor.builder()
                .format("ldp_vp")
                .id("test_descriptor")
                .path("$")
                .build();

        return PresentationSubmission.builder()
                .id("test_ldp_vc_presentation_definition")
                .definitionId("ldp_vc")
                .descriptorMap(List.of(descriptor))
                .build();
    }

    public String createVerifiablePresentation(String vc, List<String> revealedData, String nonce) {
        JsonObject vcWithBaseProof = JsonParser.parseString(vc).getAsJsonObject();
        String baseProof = vcWithBaseProof.get("proof").getAsJsonObject().get("proof_value").getAsString();
        return this.cryptoSuite.addDerivedProof(vc, baseProof, revealedData, nonce);
    }

    public String createVerifiablePresentationUrlEncoded(String vc, List<String> revealedData, String nonce) {
        String unencodedVpToken = createVerifiablePresentation(vc, revealedData, nonce);
        return Base64.getUrlEncoder().encodeToString(unencodedVpToken.getBytes(StandardCharsets.UTF_8));
    }
}
