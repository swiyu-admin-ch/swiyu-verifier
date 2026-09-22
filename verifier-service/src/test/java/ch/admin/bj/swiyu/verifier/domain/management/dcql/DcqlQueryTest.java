package ch.admin.bj.swiyu.verifier.domain.management.dcql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

public class DcqlQueryTest {
    @Test
    void testAddTrustedAuthorityDids() {
        var acceptedDids = List.of("did:webvh:scid:example", "did:webvh:scid:example2");
        var existingTrustedAuthory = TrustedAuthority.builder().values(List.of("did:webvh:scid:existing")).build();
        var query = DcqlQuery.builder().credentials(List.of(
            DcqlCredential.builder().id("no_authority").build(), // credential without trusted authority
            DcqlCredential.builder().id("existing_authority").trustedAuthorities(List.of(existingTrustedAuthory)).build()) // credential with existing authority
        ).build();
        query.addTrustedAuthorityDids(acceptedDids);
        for(DcqlCredential cred : query.getCredentials()) {
            if (cred.getId() == "no_authority") {
                assertThat(cred.getTrustedAuthorities()).isNotNull().hasSize(1);
                assertThat(cred.getTrustedAuthorities().getFirst().getValues()).hasSize(2)
                .as("Whe no authority was specified should use accepted issuers").containsAll(acceptedDids);
            } else if (cred.getId() == "existing_authority") {
                assertThat(cred.getTrustedAuthorities()).isNotNull().hasSize(1);
                assertThat(cred.getTrustedAuthorities().getFirst().getValues()).hasSize(1)
                .as("When trusted authority was provided by the business verifier it should not be overridden").containsAll(existingTrustedAuthory.getValues());
            }
        }
    }
}
