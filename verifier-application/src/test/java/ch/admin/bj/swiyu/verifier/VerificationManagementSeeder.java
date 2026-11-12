package ch.admin.bj.swiyu.verifier;

import ch.admin.bj.swiyu.verifier.common.DcqlTestHelper;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;

/**
 * Utility for seeding the verification management fixtures used by the integration tests, so that
 * the scenario data is defined once instead of being duplicated across test base classes.
 *
 * <p>Not instantiable; the class only exposes static members.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VerificationManagementSeeder {

    /**
     * Id under which the fixtures' DCQL queries request the credential and under which the tests
     * therefore submit the presented credential.
     */
    public static final String DEFAULT_DCQL_CREDENTIAL_ID = "defaultTestDcqlCredentialId";

    /**
     * The well-known identifiers the integration tests address their verification fixtures by, one
     * component per scenario. The values are fixed on purpose, because the fixtures are seeded and
     * later looked up by exactly these ids.
     *
     * @param secured                          plain request with a JWT-secured request object
     * @param sdjwtMgmtNoSignature             request whose request object is not JWT-secured
     * @param expired                          request that is already expired
     * @param withoutAcceptedIssuer            request without an accepted issuer DID
     * @param differentAlgs                    request used by the differing signing algorithm scenario
     * @param differentKbAlgs                  request used by the differing key-binding algorithm scenario
     * @param sdjwtResponseEncrypted           request expecting an encrypted response (direct_post.jwt)
     * @param withDcqlAndHolderBinding         DCQL query requiring cryptographic holder binding
     * @param withDcqlAndOptionalHolderBinding DCQL query with optional cryptographic holder binding
     * @param nestedSecured                    JWT-secured request object with claims on nested objects
     */
    public record FixtureIds(
            UUID secured,
            UUID sdjwtMgmtNoSignature,
            UUID expired,
            UUID withoutAcceptedIssuer,
            UUID differentAlgs,
            UUID differentKbAlgs,
            UUID sdjwtResponseEncrypted,
            UUID withDcqlAndHolderBinding,
            UUID withDcqlAndOptionalHolderBinding,
            UUID nestedSecured
    ) {
    }

    /**
     * The fixture identifiers of the standard scenarios.
     */
    public static final FixtureIds FIXTURES = new FixtureIds(
            UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee1"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee2"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee3"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee4"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeafbee5"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeaf1337"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeaf1338"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeaf1339"),
            UUID.fromString("deadbeef-dead-dead-dead-deaddeaf1340")
    );

    /**
     * Description of a single verification management fixture, so that the scenarios differ only in
     * their data instead of being spelled out as one builder chain each.
     *
     * @param id                             id of the seeded management entity
     * @param nonce                          request nonce stored on the entity and embedded in wallet responses
     * @param state                          verification state of the seeded entity
     * @param expirationInSeconds            validity window of the request in seconds
     * @param expiresAt                      absolute expiry of the request as epoch milliseconds
     * @param acceptedIssuerDids             accepted issuer DIDs, empty when none are accepted
     * @param jwtSecuredAuthorizationRequest whether the request object is JWT-secured
     * @param dcqlQuery                      supplies the DCQL query of the request; invoked when the entity is built
     * @param oauthState                     OAuth state of the request, {@code null} when unused
     * @param responseSpecification          response specification; {@code null} keeps the entity default (direct_post)
     */
    public record FixtureConfig(
            UUID id,
            String nonce,
            VerificationStatus state,
            int expirationInSeconds,
            long expiresAt,
            List<String> acceptedIssuerDids,
            boolean jwtSecuredAuthorizationRequest,
            Supplier<DcqlQuery> dcqlQuery,
            String oauthState,
            ResponseSpecification responseSpecification
    ) {
    }

    /**
     * Builds the configuration of every standard scenario. All fixtures share the given request
     * nonce, matching the nonce the tests embed in the wallet responses they submit.
     *
     * @param nonce request nonce stored on every fixture
     * @return the fixture configurations, in the order they are seeded
     * @throws JOSEException if the ephemeral key of the encrypted-response fixture cannot be generated
     */
    private static List<FixtureConfig> getFixtureConfigs(String nonce) throws JOSEException {
        var secured = new FixtureConfig(
                FIXTURES.secured(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                true,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()),
                FIXTURES.secured().toString(),
                null
        );

        var sdjwtMgmtNoSignature = new FixtureConfig(
                FIXTURES.sdjwtMgmtNoSignature(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                false,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()),
                null,
                null
        );

        var requestIdNestedSecured = new FixtureConfig(
                FIXTURES.nestedSecured(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                true,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryNestedObjectJson()),
                FIXTURES.nestedSecured().toString(),
                null
        );

        var requestIdExpired = new FixtureConfig(
                FIXTURES.expired(),
                nonce,
                PENDING,
                86400,
                0,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                false,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()),
                FIXTURES.expired().toString(),
                null
        );

        var requestIdWithoutAcceptedIssuer = new FixtureConfig(
                FIXTURES.withoutAcceptedIssuer(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(),
                false,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()),
                null,
                null
        );

        var requestIdWithDCQLAndHolderBinding = new FixtureConfig(
                FIXTURES.withDcqlAndHolderBinding(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                false,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJsonWithCryptographicHolderBinding(true)),
                FIXTURES.withDcqlAndHolderBinding().toString(),
                null
        );

        var requestIdWithDCQLAndOptionalHolderBinding = new FixtureConfig(
                FIXTURES.withDcqlAndOptionalHolderBinding(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                false,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJsonWithCryptographicHolderBinding(false)),
                FIXTURES.withDcqlAndOptionalHolderBinding().toString(),
                null
        );

        var differentAlgs = new FixtureConfig(
                FIXTURES.differentAlgs(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                true,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()),
                null,
                null
        );

        var differentKbAlgs = new FixtureConfig(
                FIXTURES.differentKbAlgs(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                false,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()),
                null,
                null
        );

        var ephemeralEncryptionKey = new ECKeyGenerator(Curve.P_256)
                .keyID(UUID.randomUUID().toString())
                .algorithm(JWEAlgorithm.ECDH_ES)
                .generate();

        var requestIdWithSDJWTResponseEncrypted = new FixtureConfig(
                FIXTURES.sdjwtResponseEncrypted(),
                nonce,
                PENDING,
                86400,
                4070908800000L,
                List.of(SDJWTCredentialMock.DEFAULT_ISSUER_ID),
                true,
                () -> DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson()),
                FIXTURES.sdjwtResponseEncrypted().toString(),
                ResponseSpecification.builder()
                        .responseModeType(ResponseModeType.DIRECT_POST_JWT)
                        .jwksPrivate(new JWKSet(ephemeralEncryptionKey).toString(false))
                        .jwks(new JWKSet(ephemeralEncryptionKey).toString(true))
                        .encryptedResponseEncValuesSupported(List.of("A256GCM"))
                        .build()
        );

        return List.of(secured, sdjwtMgmtNoSignature, requestIdNestedSecured, requestIdExpired, requestIdWithoutAcceptedIssuer, requestIdWithDCQLAndHolderBinding, requestIdWithDCQLAndOptionalHolderBinding, differentAlgs, differentKbAlgs, requestIdWithSDJWTResponseEncrypted);
    }

    /**
     * Creates the management entities of all standard scenarios, ready to be persisted by the caller.
     *
     * @param nonce request nonce stored on every fixture
     * @return unsaved management entities, one per fixture configuration
     * @throws JOSEException if an encrypted-response fixture cannot generate its ephemeral key
     */
    public static List<Management> createAll(String nonce) throws JOSEException {
        return getFixtureConfigs(nonce).stream()
                .map(VerificationManagementSeeder::createManagement)
                .toList();
    }

    /**
     * Returns the default DCQL query, which requests the SD-JWT VC credential format.
     *
     * @return the DCQL query JSON for {@link DcqlTestHelper#DC_SD_JWT_CREDENTIAL_FORMAT}
     */
    private static String dcqlQueryJson() {
        return dcqlQueryJson(DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT);
    }

    /**
     * Returns the default DCQL query for the given credential format: one credential whose
     * last_name, first_name and languages claims are requested, the latter restricted to Italian.
     *
     * @param format credential format requested by the query
     * @return the DCQL query JSON
     */
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

    /**
     * Returns the DCQL query that requests claims on nested objects: the street and country of each
     * entry in the addresses array, both restricted to a fixed value.
     *
     * @return the DCQL query JSON with claim paths into the addresses array
     */
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

    /**
     * Returns the DCQL query that requests last_name and first_name and controls whether
     * cryptographic holder binding is required.
     *
     * @param requireCryptographicHolderBinding whether the query requires holder binding ({@code true}) or
     *                                         leaves it optional ({@code false})
     * @return the DCQL query JSON
     */
    private static String dcqlQueryJsonWithCryptographicHolderBinding(boolean requireCryptographicHolderBinding) {
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

    /**
     * Maps a fixture configuration to an unsaved management entity.
     *
     * @param config fixture to map
     * @return the management entity; when the config carries no response specification, the entity
     *         keeps the default of {@link Management} (direct_post)
     */
    private static Management createManagement(FixtureConfig config) {
        var mgmt = Management.builder()
                .id(config.id)
                .requestNonce(config.nonce)
                .state(config.state)
                .walletResponse(null)
                .expirationInSeconds(config.expirationInSeconds)
                .expiresAt(config.expiresAt)
                .acceptedIssuerDids(config.acceptedIssuerDids)
                .jwtSecuredAuthorizationRequest(config.jwtSecuredAuthorizationRequest)
                .dcqlQuery(config.dcqlQuery.get());
        if (config.responseSpecification != null) {
            mgmt.responseSpecification(config.responseSpecification);
        }

        if (config.oauthState != null) {
            mgmt.oauthState(config.oauthState);
        }

        return mgmt.build();
    }
}
