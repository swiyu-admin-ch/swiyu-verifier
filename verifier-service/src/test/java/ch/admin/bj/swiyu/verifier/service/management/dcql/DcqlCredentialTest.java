package ch.admin.bj.swiyu.verifier.service.management.dcql;

import org.junit.jupiter.api.Test;

import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;

import static org.assertj.core.api.Assertions.assertThat;

class DcqlCredentialTest {

    @Test
    void testIsCryptographicHolderBindingRequired_whenNull_thenTrue() {
        DcqlCredential credential = new DcqlCredential();
        // Setting requireCryptographicHolderBinding to null
        credential.setRequireCryptographicHolderBinding(null);

        boolean result = credential.isCryptographicHolderBindingRequired();

        assertThat(result).as("By default holder binding is required").isTrue();
    }

    @Test
    void testIsCryptographicHolderBindingRequired_whenTrue_thenTrue() {
        DcqlCredential credential = new DcqlCredential();
        // Setting requireCryptographicHolderBinding to true
        credential.setRequireCryptographicHolderBinding(true);

        boolean result = credential.isCryptographicHolderBindingRequired();

        assertThat(result).isTrue();
    }

    @Test
    void testIsCryptographicHolderBindingRequired_whenFalse_thenFalse() {
        DcqlCredential credential = new DcqlCredential();
        // Setting requireCryptographicHolderBinding to false
        credential.setRequireCryptographicHolderBinding(false);

        boolean result = credential.isCryptographicHolderBindingRequired();

        assertThat(result).isFalse();
    }
}