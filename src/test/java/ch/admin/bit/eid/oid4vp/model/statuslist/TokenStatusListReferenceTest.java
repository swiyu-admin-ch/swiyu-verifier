package ch.admin.bit.eid.oid4vp.model.statuslist;

import ch.admin.bit.eid.oid4vp.model.IssuerPublicKeyLoader;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bit.eid.oid4vp.mock.ManagementEntityMock.getManagementEntityMock;
import static ch.admin.bit.eid.oid4vp.mock.PresentationDefinitionMocks.createPresentationDefinitionMock;

@ActiveProfiles("test")
@SpringBootTest
public class TokenStatusListReferenceTest {
    /*StatusListResolverAdapter adapter,
    Map<String, Object> statusListReferenceClaims,
    ManagementEntity presentationManagementEntity,
    IssuerPublicKeyLoader issuerPublicKeyLoader*/
    @MockBean
    private StatusListResolverAdapter adapter;
    @Autowired
    IssuerPublicKeyLoader issuerPublicKeyLoader;

    Map<String, Object> statusListReferenceClaims;

    void setup() throws Exception {


    }

    @Test
    void testSuccessfulVerification() {
        var id = UUID.randomUUID();
        var presentationDefinition = createPresentationDefinitionMock(id, List.of("$.first_name", "$.last_name", "$.birthdate"));
        var presentationManagementEntity = getManagementEntityMock(id, presentationDefinition);

        TokenStatusListReference tokenStatusList = new TokenStatusListReference(adapter, statusListReferenceClaims, presentationManagementEntity, issuerPublicKeyLoader);
        tokenStatusList.verifyStatus();
    }
}
