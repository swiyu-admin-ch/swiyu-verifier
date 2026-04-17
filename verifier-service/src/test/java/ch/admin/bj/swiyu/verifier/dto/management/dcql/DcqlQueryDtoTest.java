package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialSet;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.service.management.DcqlMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;

class DcqlQueryDtoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testSerializationDcqlQuery () throws JsonProcessingException {
        var dcqlQuery = new DcqlQuery();
        DcqlMapper.toDcqlQueryDto(dcqlQuery);
        assertNoNullContained(dcqlQuery);

        var dcqlCredentialBuilder = DcqlCredential.builder().id("My CredenitalQuery").format("dc+sd-jwt").meta(DcqlCredentialMeta.builder().vctValues(List.of("test")).build());
        var credentials = new LinkedList<DcqlCredential>();
        dcqlQuery.setCredentials(credentials);
        credentials.add(dcqlCredentialBuilder.build());
        assertNoNullContained(dcqlQuery);
        dcqlCredentialBuilder.claims(List.of(DcqlClaim.builder().build()));
        credentials.add(dcqlCredentialBuilder.build());
        assertNoNullContained(dcqlQuery);
    }

    @Test
    void testSerializationDcqlQueryCredentialSet() throws JsonProcessingException {
        var dcqlQuery = new DcqlQuery();
        DcqlMapper.toDcqlQueryDto(dcqlQuery);
        assertNoNullContained(dcqlQuery);
        var credentialSetBuilder = DcqlCredentialSet.builder();
        dcqlQuery.setCredentialSets(List.of(credentialSetBuilder.build()));
        assertNoNullContained(dcqlQuery);
    }

    private String assertNoNullContained(DcqlQuery dcqlQuery) throws JsonProcessingException {
        var serialized = objectMapper.writeValueAsString(DcqlMapper.toDcqlQueryDto(dcqlQuery));
        assertThat(serialized).doesNotContain("null");
        return serialized;
    }

}