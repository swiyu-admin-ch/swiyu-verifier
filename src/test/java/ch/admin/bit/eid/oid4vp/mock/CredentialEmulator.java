package ch.admin.bit.eid.oid4vp.mock;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import uniffi.api.KeyPair;
import uniffi.cryptosuite.BbsCryptoSuite;
import uniffi.cryptosuite.CryptoSuiteOptions;
import uniffi.cryptosuite.CryptoSuiteType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CredentialEmulator {
    private final BbsCryptoSuite cryptoSuite;
    
    public static final String ExampleJson = "{\"issuer\":\"did:example:12345\", \"type\": [\"VerifiableCredential\", \"ExampleCredential\"], \"credentialSubject\": {\"hello\":\"world\"}}";

    public CredentialEmulator() {
        this.cryptoSuite = new BbsCryptoSuite(new KeyPair());
    }

    public String createVC(List<String> requiredFields, String vcCredentialSubjectDataJson){
        CryptoSuiteOptions options = new CryptoSuiteOptions(
                requiredFields,
                CryptoSuiteType.BBS2023,
                "did:example:12345");
        return this.cryptoSuite.addProof(vcCredentialSubjectDataJson, options);
    }

    public String getCredentialSubmission() {
        return "{\"id\":\"test_ldp_vc_presentation_definition\",\"definition_id\":\"ldp_vc\",\"descriptor_map\":[{\"id\":\"test_descriptor\",\"format\":\"ldp_vp\",\"path\":\"$.credentialSubject\"}]}";
    }

    public String createCredentialSubmissionURLEncoder() {
        return URLEncoder.encode(getCredentialSubmission(), StandardCharsets.UTF_8);
    }

    public String createVerifiablePresentation(String verifiableCredential, List<String> revealedData, String presentationNonce) {
        JsonObject vcWithBaseProof = JsonParser.parseString(verifiableCredential).getAsJsonObject();
        String baseProof = vcWithBaseProof.get("proof").getAsJsonObject().get("proof_value").getAsString();
        return this.cryptoSuite.addDerivedProof(
                verifiableCredential,
                baseProof,
                revealedData,
                presentationNonce);
    }
}
