package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.verifier.AbstractMockMvcIT;
import ch.admin.bj.swiyu.verifier.VerificationManagementSeeder;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Shared base for the OID4VP integration tests in this package: retrieval of the request object,
 * submission or rejection of the wallet response, and the resulting management state.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class AbstractVerificationControllerIT extends AbstractMockMvcIT {

    public static final String PUBLIC_KEY = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"oqBwmYd3RAHs-sFe_U7UFTXbkWmPAaqKTHCvsV8tvxU\",\"y\":\"np4PjpDKNfEDk9qwzZPqjAawiZ8sokVOozHR-Kt89T4\"}";

    protected static final String NONCE_SD_JWT_SQL = "P2vZ8DKAtTuCIU1M7daWLA65Gzoa76tL";

    @Autowired
    protected ManagementRepository managementEntityRepository;

    @MockitoBean
    protected DidResolverFacade didResolverFacade;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoSpyBean
    protected ApplicationProperties applicationProperties;

    @BeforeEach
    void setUp() throws JacksonException, JOSEException {
        List<Management> fixtures = VerificationManagementSeeder.createAll(NONCE_SD_JWT_SQL);
        managementEntityRepository.saveAll(fixtures);
    }

    @AfterEach
    void cleanup() {
        managementEntityRepository.deleteAll();
    }

    static ManagementResponseDto createVerificationRequest(MockMvc mvc, CreateVerificationManagementDto createVerificationManagementDto) {
        ObjectMapper objectMapper = new ObjectMapper();

        var body = assertDoesNotThrow(() -> objectMapper.writeValueAsString(createVerificationManagementDto));
        MvcResult createVerificationResult = assertDoesNotThrow(() -> mvc.perform(post("/management/api/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
        );

        return assertDoesNotThrow(() -> objectMapper.readValue(createVerificationResult.getResponse().getContentAsString(), ManagementResponseDto.class));
    }

    void mockDidResolverResponse(SDJWTCredentialMock sdjwt) {
        try {
            // Parse the JSON Web Key string into a Nimbus JWK object to ensure correct type
            JWK nimbusJwk = JWK.parse(KeyFixtures.issuerPublicKeyAsJsonWebKey());
            when(didResolverFacade.resolveKey(sdjwt.getKidHeaderValue())).thenReturn(nimbusJwk);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    SignedJWT getRequestObject(String requestUri) throws Exception {
        String content = mockMvc.perform(get(requestUri)
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return SignedJWT.parse(content);
    }

    ResultActions sendVerificationResponse(String verificationUrl, String vpToken, SignedJWT requestObject) throws Exception {
        var builder = post(verificationUrl)
                .contentType(APPLICATION_FORM_URLENCODED_VALUE);

        ResponseModeTypeDto responseModeTypeDto = getResponseModeTypeDtoFromRequestObject(requestObject);
        JsonNode claimsNode = objectMapper.valueToTree(requestObject.getJWTClaimsSet().getClaims());

        var dcqlId = claimsNode.get("dcql_query").get("credentials").get(0).get("id").stringValue();

        var submissionData = objectMapper.writeValueAsString(Map.of(dcqlId, List.of(vpToken)));

        var state = getStateFromRequestObject(requestObject);

        if (responseModeTypeDto.equals(ResponseModeTypeDto.DIRECT_POST)) {
            builder
                    .formField("state", state)
                    .formField("vp_token", submissionData);
        } else if (responseModeTypeDto.equals(ResponseModeTypeDto.DIRECT_POST_JWT)) {
            builder.formField("response", encryptResponse(Map.of("vp_token", submissionData, "state", state), requestObject));
        }

        return mockMvc.perform(builder);
    }

    ResultActions sendVerificationRejection(String verificationUrl, String error, String errorDescription, SignedJWT requestObject) throws Exception {

        var builder = post(verificationUrl)
                .contentType(APPLICATION_FORM_URLENCODED_VALUE);
        var state = getStateFromRequestObject(requestObject);
        ResponseModeTypeDto responseModeTypeDto = getResponseModeTypeDtoFromRequestObject(requestObject);

        if (responseModeTypeDto.equals(ResponseModeTypeDto.DIRECT_POST)) {
            builder
                    .formField("state", state)
                    .formField("error", error)
                    .formField("error_description", errorDescription);
        } else if (responseModeTypeDto.equals(ResponseModeTypeDto.DIRECT_POST_JWT)) {
            builder.formField("response", encryptResponse(Map.of("error", error, "error_description", errorDescription, "state", state), requestObject));
        }

        return mockMvc.perform(builder);
    }

    String getStateFromRequestObject(SignedJWT requestObject) throws ParseException {
        return requestObject.getJWTClaimsSet().getStringClaim("state");
    }

    ResponseModeTypeDto getResponseModeTypeDtoFromRequestObject(SignedJWT requestObject) throws Exception {
        JsonNode requestObjectNode = objectMapper.valueToTree(requestObject.getJWTClaimsSet().getClaims());
        return ResponseModeTypeDto.fromValue(requestObjectNode.get("response_mode").asString());
    }

    String encryptResponse(Map<String, String> fields, SignedJWT requestObjectDto) throws ParseException, JOSEException {
        JsonNode requestObjectNode = objectMapper.valueToTree(requestObjectDto.getJWTClaimsSet().getClaims());
        JsonNode metadata = requestObjectNode.get("client_metadata");
        var JWKsNode = metadata.get("jwks");
        var keys = JWKsNode.get("keys").asArray();
        var jwkString = objectMapper.writeValueAsString(keys.get(0));
        var jwk = JWK.parse(jwkString);
        var encryptionMethod = EncryptionMethod.parse(metadata.get("encrypted_response_enc_values_supported").asArray().get(0).asString());

        var claims = new JWTClaimsSet.Builder();
        fields.forEach(claims::claim);

        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, encryptionMethod)
                        .keyID(jwk.getKeyID()).build(),
                claims.build().toPayload()
        );
        jweObject.encrypt(new ECDHEncrypter(jwk.toECKey()));
        return jweObject.serialize();
    }
}