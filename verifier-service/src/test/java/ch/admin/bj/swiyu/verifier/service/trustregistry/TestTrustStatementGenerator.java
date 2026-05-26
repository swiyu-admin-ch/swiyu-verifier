package ch.admin.bj.swiyu.verifier.service.trustregistry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.text.ParseException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestTrustStatementGenerator {

    private final ECKey key;

    public SignedJWT generateIdTsJwt(String trustIssuerKid, String subject) {
        return assertDoesNotThrow(() -> createTestSignedJwt("""
                    {
                    "typ": "swiyu-identity-trust-statement+jwt",
                    "alg": "ES256",
                    "kid": "%s",
                	"profile_version": "swiss-profile-trust:1.0.0"
                }""".formatted(trustIssuerKid),
                """
                        {
                            "sub": "%s",
                            "iat": 1690360968,
                            "exp": 32503676400,
                            "status":  {
                                "status_list": {
                                  "idx": 1,
                                  "uri": "https://example.com/statuslists/1"
                                }
                            },
                            "entity_name": "John Smith's Smithery",
                            "entity_name#de": "John Smith's Schmiderei",
                            "entity_name#de-CH": "John Smith's Schmiderei",
                            "is_state_actor": false,
                            "registry_ids": [
                              {
                                "type": "UID",
                                "value": "CHE-000.000.000"
                              },
                              {
                                "type": "LEI",
                                "value": "0A1B2C3D4E5F6G7H8J9I"
                              }
                            ]
                        }
                         """.formatted(subject)));
    }

    public SignedJWT generatePiaTsJwt(String trustIssuerKid, String subject, String vct) {
        return assertDoesNotThrow(() -> createTestSignedJwt("""
                        {
                    "typ": "swiyu-protected-issuance-authorization-trust-statement+jwt",
                    "alg": "ES256",
                    "kid": "%s",
                	"profile_version": "swiss-profile-trust:1.0.0"
                }""".formatted(trustIssuerKid),
                """
                        {
                            "jti": "07f289d5-8b1f-4604-bf72-53bdcb71ee05",
                            "sub": "%s",
                            "iat": 1690360968,
                            "exp": 32503676400,
                            "status": {
                                "status_list": {
                                "idx": 1,
                                "uri": "https://example.com/statuslists/1"
                                }
                            },
                            "can_issue": {
                                "vct": "%s",
                                "vct_name": "Test Credential",
                                "reason": "This issuer is eglible to issue Test credentials"
                            }
                        }
                        """.formatted(subject, vct)));
    }

    public SignedJWT generatePiTlsJwt(String trustIssuerKid, String vct) {
        return assertDoesNotThrow(() -> createTestSignedJwt("""
                         {
                    "typ": "swiyu-protected-issuance-trust-list-statement+jwt",
                    "alg": "ES256",
                    "kid": "%s",
                	"profile_version": "swiss-profile-trust:1.0.0"
                }""".formatted(trustIssuerKid),
                """
                                {
                          "jti": "07f289d5-8b1f-4604-bf72-53bdcb71ee05",
                          "iat": 1690360968,
                          "exp": 32503676400,
                          "status": {
                            "status_list": {
                              "idx": 1,
                              "uri": "https://example.com/statuslists/1"
                            }
                          },
                          "vct_values": [
                            "%s",
                            "urn:ch.admin.fedpol.eid",
                            "urn:ch.admin.fedpol.betaid",
                            "urn:com.example.otherCredential"
                          ]
                        }
                        """.formatted(vct)));
    }

    public SignedJWT generateNcTlsJwt(String trustIssuerKid, String nonCompliantActor) {
        return assertDoesNotThrow(() -> createTestSignedJwt("""
                {
                    "typ": "swiyu-non-compliance-trust-list-statement+jwt",
                    "alg": "ES256",
                    "kid": "%s",
                	"profile_version": "swiss-profile-trust:1.0.0"
                }""".formatted(trustIssuerKid),
                """
                        {
                          "iat": 1690360968,
                          "exp": 32503676400,
                          "status": {
                            "status_list": {
                              "idx": 1,
                              "uri": "https://example.com/statuslists/1"
                            }
                          },
                          "non_compliant_actors": [
                            {
                              "actor": "%s",
                              "flagged_at": "2026-02-25T07:07:35Z",
                              "reason": "The issuer is not who they claim to be (DE)",
                              "reason#de": "The issuer is not who they claim to be (DE)",
                              "reason#en": "The issuer is not who they claim to be (EN)",
                              "reason#fr-CH": "The issuer is not who they claim to be (FR)",
                              "reason#it-CH": "The issuer is not who they claim to be (IT)",
                              "reason#rm-CH": "The issuer is not who they claim to be (RM)"
                            },
                            {
                              "actor": "did:example:badActor2",
                              "flagged_at": "2025-01-13T07:13:00Z",
                              "reason": "The verifier is not who they claim to be (DE)",
                              "reason#de": "The verifier is not who they claim to be (DE)",
                              "reason#en": "The verifier is not who they claim to be (EN)",
                              "reason#fr-CH": "The verifier is not who they claim to be (FR)",
                              "reason#it-CH": "The verifier is not who they claim to be (IT)",
                              "reason#rm-CH": "The verifier is not who they claim to be (RM)"
                            }
                          ]
                        }
                            """.formatted(nonCompliantActor)));
    }

    /**
     * Generates a status list token as in
     * https://www.ietf.org/archive/id/draft-ietf-oauth-status-list-20.html#appendix-C.1
     */
    public String generateTokenStatusList(String trustRootKid, String statusListUri) {

        return assertDoesNotThrow(() -> createTestSignedJwt("""
                            {
                  "alg": "ES256",
                  "kid": "%s",
                  "typ": "statuslist+jwt",
                  "profile_version": "swiss-profile-vc:1.0.0"
                }""".formatted(trustRootKid),
                """
                        {
                            "status_list": {
                                "bits": 1,
                                "lst": "eNrt3AENwCAMAEGogklACtKQPg9LugC9k_ACvreiogEAAKkeCQAAAAAAAAAAAAAAAAAAAIBylgQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAXG9IAAAAAAAAAPwsJAAAAAAAAAAAAAAAvhsSAAAAAAAAAAAA7KpLAAAAAAAAAAAAAAAAAAAAAJsLCQAAAAAAAAAAADjelAAAAAAAAAAAKjDMAQAAAACAZC8L2AEb"
                            },
                            "iat": 1690360968,
                            "exp": 32503676400,
                            "ttl": 5,
                            "sub": "%s"
                        }
                                        """
                        .formatted(statusListUri))
                .serialize());
    }

    private SignedJWT createTestSignedJwt(String headerJson, String bodyJson) throws ParseException, JOSEException {
        SignedJWT jwt = new SignedJWT(JWSHeader.parse(headerJson), JWTClaimsSet.parse(bodyJson));
        jwt.sign(new ECDSASigner(key));
        return jwt;

    }

}
