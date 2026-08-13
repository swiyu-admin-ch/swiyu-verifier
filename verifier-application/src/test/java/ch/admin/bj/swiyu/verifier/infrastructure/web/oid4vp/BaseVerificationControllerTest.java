package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.common.DcqlTestHelper;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class BaseVerificationControllerTest {

    protected static final UUID REQUEST_ID_SECURED = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    protected static final UUID REQUEST_ID_SDJWT_MGMT_NO_SIGNATURE = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee1");
    protected static final UUID REQUEST_ID_EXPIRED = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee2");
    protected static final UUID REQUEST_ID_WITHOUT_ACCEPTED_ISSUER = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee3");
    protected static final UUID REQUEST_DIFFERENT_ALGS = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee4");
    protected static final UUID REQUEST_DIFFERENT_KB_ALGS = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee5");
    protected static final UUID REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED = UUID.fromString("deadbeef-dead-dead-dead-deaddeaf1337");
    protected static final UUID REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING= UUID.fromString("deadbeef-dead-dead-dead-deaddeaf1338");
    protected static final UUID REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING= UUID.fromString("deadbeef-dead-dead-dead-deaddeaf1339");
    protected static final UUID REQUEST_ID_NESTED_SECURED= UUID.fromString("deadbeef-dead-dead-dead-deaddeaf1340");

    protected static final String NONCE_SD_JWT_SQL = "P2vZ8DKAtTuCIU1M7daWLA65Gzoa76tL";
    protected static final String DEFAULT_DCQL_CREDENTIAL_ID = "defaultTestDcqlCredentialId";

    @Autowired
    protected ManagementRepository managementEntityRepository;

    @MockitoBean
    protected DidResolverFacade didResolverFacade;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws JacksonException, JOSEException {

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_SDJWT_MGMT_NO_SIGNATURE)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .jwtSecuredAuthorizationRequest(false)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_SECURED)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .oauthState(REQUEST_ID_SECURED.toString())
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .jwtSecuredAuthorizationRequest(true)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_NESTED_SECURED)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .walletResponse(null)
                .oauthState(REQUEST_ID_NESTED_SECURED.toString())
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .jwtSecuredAuthorizationRequest(true)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryNestedObjectJson()))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_EXPIRED)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .oauthState(REQUEST_ID_EXPIRED.toString())
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(0)
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_WITHOUT_ACCEPTED_ISSUER)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .oauthState(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING.toString())
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJsonWithCryptographicHolderBinding(true)))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .oauthState(REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING.toString())
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJsonWithCryptographicHolderBinding(false)))
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_DIFFERENT_ALGS)
                .jwtSecuredAuthorizationRequest(true)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()))
                .build());

        managementEntityRepository.save(Management.builder()
                .id(REQUEST_DIFFERENT_KB_ALGS)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()))
                .build());


        var ephemeralEncryptionKey = new ECKeyGenerator(Curve.P_256)
          .keyID(UUID.randomUUID().toString())
          .algorithm(JWEAlgorithm.ECDH_ES)
          .generate();
        JWKSet jwkSet = new JWKSet(ephemeralEncryptionKey);
        managementEntityRepository.save(Management.builder()
                .id(REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .oauthState(REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED.toString())
                .acceptedIssuerDids(List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID))
                .responseSpecification(ResponseSpecification.builder()
                        .responseModeType(ResponseModeType.DIRECT_POST_JWT)
                        .jwksPrivate(jwkSet.toString(false))
                        .jwks(jwkSet.toString(true))
                        .encryptedResponseEncValuesSupported(List.of("A256GCM"))
                        .build())
                .jwtSecuredAuthorizationRequest(true)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()))
                .build());
    }

    @AfterEach
    void cleanup() {
        managementEntityRepository.deleteAll();
    }

    public static String dcqlQueryJson() {
        return dcqlQueryJson(DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT);
    }

    public static String dcqlQueryJson(String format) {
        return """
                {
                "credentials": [
                    {
                      "id": "%s",
                      "format": "%s",
                      "meta": {
                        "vct_values": [ "%s" ]
                      },
                      "claims": [
                          {"path": ["last_name"]},
                          {"path": ["first_name"]},
                          {"path": ["languages", null], "values": ["IT"]}
                      ]
                    }
                  ]
                }
                """.formatted(DEFAULT_DCQL_CREDENTIAL_ID, format, SDJWTCredentialMock.DEFAULT_VCT);
    }

    private static String dcqlQueryNestedObjectJson() {
        return """
                {
                "credentials": [
                    {
                      "id": "%s",
                      "format": "%s",
                      "meta": {
                        "vct_values": [ "%s" ]
                      },
                      "claims": [
                          {"path": ["addresses", null, "street"], "values": ["Bahnhofstrasse"]},
                          {"path": ["addresses", null, "country"], "values": ["CH"]}
                      ]
                    }
                  ]
                }
                """.formatted(DEFAULT_DCQL_CREDENTIAL_ID, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT, SDJWTCredentialMock.DEFAULT_VCT);
    }

    static String dcqlQueryJsonWithCryptographicHolderBinding(boolean requireCryptographicHolderBinding) {
        return """
                {
                "credentials": [
                    {
                      "id": "%s",
                      "format": "%s",
                      "meta": {
                        "vct_values": [ "%s" ]
                      },
                      "require_cryptographic_holder_binding": %s,
                      "claims": [
                          {"path": ["last_name"]},
                          {"path": ["first_name"]}
                      ]
                    }
                  ]
                }
                """.formatted(DEFAULT_DCQL_CREDENTIAL_ID, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT, SDJWTCredentialMock.DEFAULT_VCT, requireCryptographicHolderBinding);
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

        var state = requestObject.getJWTClaimsSet().getStringClaim("state");

        JsonNode claimsNode = objectMapper.valueToTree(requestObject.getJWTClaimsSet().getClaims());

        var dcqlId = claimsNode.get("dcql_query").get("credentials").get(0).get("id").stringValue();

        var submissionData = objectMapper.writeValueAsString(Map.of(dcqlId, List.of(vpToken)));

        if (responseModeTypeDto.equals(ResponseModeTypeDto.DIRECT_POST)) {
            builder
                    .formField("state", state)
                    .formField("vp_token", submissionData);
        } else if (responseModeTypeDto.equals(ResponseModeTypeDto.DIRECT_POST_JWT)) {
            builder.formField("response", encryptResponse(Map.of("vp_token", submissionData, "state", state), requestObject));
        }

        return mockMvc.perform(builder);
    }

    ResponseModeTypeDto getResponseModeTypeDtoFromRequestObject(SignedJWT requestObject) throws Exception {
        JsonNode requestObjectNode = objectMapper.valueToTree(requestObject.getJWTClaimsSet().getClaims());
        var teest = requestObjectNode.get("response_mode").asString();
        return ResponseModeTypeDto.fromValue(teest);
    }

    private String encryptResponse(Map<String,String> fields, SignedJWT requestObjectDto) throws ParseException, JOSEException {
        JsonNode requestObjectNode = objectMapper.valueToTree(requestObjectDto.getJWTClaimsSet().getClaims());
        JsonNode metadata = requestObjectNode.get("client_metadata");
        var JWKsNode = metadata.get("jwks");
        var keys = JWKsNode.get("keys").asArray();
        var jwkString = objectMapper.writeValueAsString(keys.get(0));
        var jwk = JWK.parse(jwkString);
        var encryptionMethod = EncryptionMethod.parse(requestObjectNode.get("encrypted_response_enc_values_supported").asArray().get(0).asString());

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