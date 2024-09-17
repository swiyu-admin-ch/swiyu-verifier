package ch.admin.bit.eid.oid4vp.mock;


import ch.admin.bit.eid.oid4vp.config.BbsKeyProperties;
import ch.admin.bit.eid.oid4vp.model.dto.Descriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.eid.bbscryptosuite.BbsCryptoSuite;
import ch.admin.eid.bbscryptosuite.BbsDerivedProofInit;
import ch.admin.eid.bbscryptosuite.CryptoSuiteOptions;
import ch.admin.eid.bbscryptosuite.CryptoSuiteType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BBSCredentialMock {

    public static final String ExampleJson = "{\"issuer\":\"did:example:12345\", \"type\": [\"VerifiableCredential\", \"ExampleCredential\"], \"credentialSubject\": {\"hello\":\"world\"}}";
    private final BbsCryptoSuite cryptoSuite;
    private final String privateKeyString = "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEIDqMm9PvL4vpyFboAwaeViQsH30CkaDcVtRniZPezFxpoAoGCCqGSM49\nAwEHoUQDQgAEQgjeqGSdu+2jq8+n78+6fXk2Yh22lQKBYCnu5FWPvKtat3wFEsQX\nqNHYgPXBxWmOBw5l2PE/gUDUJqGJSc1LuQ==\n-----END EC PRIVATE KEY-----";
    private final ECKey holderKey;

    public BBSCredentialMock(final BbsKeyProperties bbsKeyProperties) throws JOSEException {
        cryptoSuite = new BbsCryptoSuite(bbsKeyProperties.getBBSKey());
        holderKey = JWK.parseFromPEMEncodedObjects(privateKeyString).toECKey();
    }

    @NotNull
    private static Map<String, Object> prepareVerifiablePresentationWrapper(List<HashMap> vpMap) {
        // Wrapper with Proof
        Map<String, Object> vpWrapper = new HashMap<>();
        vpWrapper.put("@context", List.of("https://www.w3.org/2018/credentials/v1"));
        vpWrapper.put("type", List.of("VerifiablePresentation"));
        vpWrapper.put("verifiableCredential", vpMap);
        vpWrapper.put("id", "presentationId");
        return vpWrapper;
    }

    public String addHolderBinding(String vcCredentialSubjectDataJson) throws JsonProcessingException {
        // Create did:jwk
        var didJwk = "did:jwk:" + Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(holderKey.toPublicJWK().toJSONString().getBytes(StandardCharsets.UTF_8));
        // Add as $.credentialSubject.id = did:jwk:...
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> vcData = objectMapper.readValue(vcCredentialSubjectDataJson, HashMap.class);
        ((Map<String, Object>) vcData.get("credentialSubject")).put("id", didJwk);
        return objectMapper.writeValueAsString(vcData);

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

    public String createBBSPresentation(String vc, List<String> revealedData, String nonce) {
        JsonObject vcWithBaseProof = JsonParser.parseString(vc).getAsJsonObject();
        String baseProof = vcWithBaseProof.get("proof").getAsJsonObject().get("proof_value").getAsString();

        BbsDerivedProofInit bbsDerivedProofInit = cryptoSuite.initDerivedProof(
                vc,
                baseProof,
                revealedData,
                nonce
        );

        return this.cryptoSuite.addDerivedProof(bbsDerivedProofInit, null);
    }

    public String createVerifiablePresentationUrlEncodedHolderBinding(String vc, List<String> revealedData, String nonce) throws JsonProcessingException, JOSEException {
        // Create BBS VP Token
        ObjectMapper mapper = new ObjectMapper();
        var vcData = mapper.readValue(vc, HashMap.class);
        String vpToken = createBBSPresentation(vc, revealedData, nonce);
        // Assemble Holder Proof of possession
        ECDSASigner signer = new ECDSASigner(holderKey);

        JWSObject signingObject = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.ES256).build(),
                new Payload(nonce)
        );
        signingObject.sign(signer);
        String proofValue = signingObject.serialize();
        // Assemble Proof
        String holderDid = ((Map<String, Object>) vcData.get("credentialSubject")).get("id").toString();
        Map<String, Object> holderBindingProof = new HashMap<>();
        holderBindingProof.put("type", "EcdsaSignature2024");
        holderBindingProof.put("created", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        holderBindingProof.put("challenge", nonce);
        holderBindingProof.put("proofValue", proofValue);
        holderBindingProof.put("proofPurpose", "authentication");
        holderBindingProof.put("verificationMethod", holderDid);

        List<HashMap> vpMap = List.of(mapper.readValue(vpToken, HashMap.class));

        Map<String, Object> vpWrapper = prepareVerifiablePresentationWrapper(vpMap);
        vpWrapper.put("holder", holderDid);
        vpWrapper.put("proof", holderBindingProof);
        var vpTokenJson = mapper.writeValueAsString(vpWrapper);
        // Encode VP Token as Base64
        return Base64.getUrlEncoder().encodeToString(vpTokenJson.getBytes(StandardCharsets.UTF_8));
    }

    public String createVerifiablePresentationUrlEncoded(String vc, List<String> revealedData, String nonce) throws JsonProcessingException {
        String vpToken = createBBSPresentation(vc, revealedData, nonce);
        ObjectMapper mapper = new ObjectMapper();
        List<HashMap> vpMap = List.of(mapper.readValue(vpToken, HashMap.class));
        Map<String, Object> vpWrapper = prepareVerifiablePresentationWrapper(vpMap);
        var vpTokenJson = mapper.writeValueAsString(vpWrapper);
        return Base64.getUrlEncoder().encodeToString(vpTokenJson.getBytes(StandardCharsets.UTF_8));
    }

}
