package ch.admin.bj.swiyu.verifier.service.sdjwt;

import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.dto.StatusVerificationResultDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListCacheService;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListVerificationService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StatusListVerificationService} focusing on trust evaluation and holder binding audience checks.
 */
class StatusListVerificationServiceTest {

    private StatusListCacheService statusListResolver;
    private TokenStatusListVerifier statusListVerifier;

    private StatusListVerificationService verifier;


    @BeforeEach
    void setUp() {
        statusListResolver = mock(StatusListCacheService.class);
        statusListVerifier = mock(TokenStatusListVerifier.class);
        verifier = new StatusListVerificationService(statusListResolver, statusListVerifier);
    }

    @Test
    void verifyStatus_whenNoStatusClaimPresent_thenReturnsEmpty() {
        Map<String, Object> claims = new HashMap<>();

        assertThat(verifier.verifyStatus(claims, new JWSHeader.Builder(JWSAlgorithm.ES256).build())).isEmpty();
    }

    @Test
    void verifyStatus_whenStatusListExists_thenReturnsVerificationResult() throws Exception {
        Map<String, Object> claims = Map.of(
                "status", Map.of(
                        "status_list", Map.of(
                                "idx", 1,
                                "uri", "https://example.com/status/1"
                        )
                )
        );
        TokenStatusListTokenDto statusListToken = mock(TokenStatusListTokenDto.class);
        StatusVerificationResultDto verificationResult = mock(StatusVerificationResultDto.class);

        when(statusListResolver.getTokenStatusListTokenByUri("https://example.com/status/1")).thenReturn(statusListToken);
        when(statusListVerifier.verifyStatus(any(), eq(statusListToken))).thenReturn(verificationResult);

        assertThat(verifier.verifyStatus(claims, new JWSHeader.Builder(JWSAlgorithm.ES256).build()))
                .contains(verificationResult);
    }

    @Test
    void verifyStatus_whenStatusListCannotBeResolved_thenThrowsUnresolvableStatusList() {
        Map<String, Object> claims = Map.of(
                "status", Map.of(
                        "status_list", Map.of(
                                "idx", 1,
                                "uri", "https://example.com/status/1"
                        )
                )
        );

        when(statusListResolver.getTokenStatusListTokenByUri("https://example.com/status/1")).thenReturn(null);

        VerificationException ex = assertThrows(VerificationException.class,
                () -> verifier.verifyStatus(claims, new JWSHeader.Builder(JWSAlgorithm.ES256).build()));

        assertEquals(UNRESOLVABLE_STATUS_LIST, ex.getErrorResponseCode());
    }
}