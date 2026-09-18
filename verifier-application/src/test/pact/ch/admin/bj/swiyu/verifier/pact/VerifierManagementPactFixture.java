package ch.admin.bj.swiyu.verifier.pact;

import ch.admin.bj.swiyu.verifier.domain.CredentialEvaluation;
import ch.admin.bj.swiyu.verifier.domain.IssuerTrustMarker;
import ch.admin.bj.swiyu.verifier.domain.VerificationResultData;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlClaimDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlCredentialDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlCredentialMetaDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlQueryDto;
import ch.admin.bj.swiyu.verifier.service.management.ManagementService;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Prepares persisted Pact states through the real verifier service and domain transitions.
 * The provider test invokes cleanup before each interaction in its isolated Testcontainers database.
 */
class VerifierManagementPactFixture {

    private final ManagementService managementService;
    private final ManagementRepository managementRepository;

    VerifierManagementPactFixture(final ManagementService managementService,
                                 final ManagementRepository managementRepository) {
        this.managementService = managementService;
        this.managementRepository = managementRepository;
    }

    Map<String, Object> createVerification(final boolean redirected, final boolean successful) {
        final var response = managementService.createVerificationManagement(verificationCreationRequest(redirected));
        final var management = managementRepository.findById(response.id()).orElseThrow();

        if (successful) {
            management.claimForProcessing();
            management.verificationDone(VerificationResultData.builder()
                    .verifiedResponsesJsonString("""
                            {
                              "VerifiableCredential": {
                                "given_name": "John",
                                "family_name": "Doe"
                              }
                            }
                            """)
                    .evaluations(Map.of("VerifiableCredential", List.of(CredentialEvaluation.builder()
                            .trustMarkers(IssuerTrustMarker.builder().isTrusted(true).build())
                            .build())))
                    .build());
            managementRepository.saveAndFlush(management);
        }

        if (redirected) {
            return Map.of(
                    "verificationId", response.id().toString(),
                    "responseCode", management.getResponseCode().toString());
        }
        return Map.of("verificationId", response.id().toString());
    }

    void cleanDatabase() {
        managementRepository.deleteAll();
    }

    private CreateVerificationManagementDto verificationCreationRequest(final boolean redirected) {
        final var metadata = new DcqlCredentialMetaDto(null, List.of("https://issuer.example.com/vct/test"), null);
        final var claim = new DcqlClaimDto(null, List.of("name"), null);
        final var credential = new DcqlCredentialDto(
                "VerifiableCredential", "vc+sd-jwt", null, metadata, List.of(claim), null, true, null);
        final var redirectUri = redirected
                ? URI.create("https://business-verifier.example.com/callback?session_nonce=pact-session")
                : null;

        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:issuer"))
                .jwtSecuredAuthorizationRequest(false)
                .responseMode(ResponseModeTypeDto.DIRECT_POST)
                .dcqlQuery(new DcqlQueryDto(List.of(credential), List.of()))
                .redirectURI(redirectUri)
                .build();
    }
}
