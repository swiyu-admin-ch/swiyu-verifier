package ch.admin.bj.swiyu.verifier.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.service.dcql.DcqlUtil;
import ch.admin.bj.swiyu.verifier.service.oid4vp.adapters.DcqlEvaluatorAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class DcqlEvaluatorAdapterTest {

    private final DcqlEvaluatorAdapter adapter = new DcqlEvaluatorAdapter();

    @Test
    void filterByVct_delegatesToDcqlUtil() {
        try (MockedStatic<DcqlUtil> utilities = mockStatic(DcqlUtil.class)) {
            List<SdJwt> input = Collections.emptyList();
            DcqlCredentialMeta meta = new DcqlCredentialMeta();
            List<SdJwt> expected = List.of(new SdJwt("dummy"));

            utilities.when(() -> DcqlUtil.filterByVct(input, meta))
                    .thenReturn(expected);

            List<SdJwt> result = adapter.filterByVct(input, meta);

            assertThat(result).isEqualTo(expected);
            utilities.verify(() -> DcqlUtil.filterByVct(input, meta));
        }
    }

    @Test
    void validateRequestedClaims_delegatesToDcqlUtil() {
        try (MockedStatic<DcqlUtil> utilities = mockStatic(DcqlUtil.class)) {
            SdJwt sdJwt = new SdJwt("dummy");
            List<DcqlClaim> claims = Collections.emptyList();

            adapter.validateRequestedClaims(sdJwt, claims);

            utilities.verify(() -> DcqlUtil.validateRequestedClaims(sdJwt, claims));
        }
    }
}

