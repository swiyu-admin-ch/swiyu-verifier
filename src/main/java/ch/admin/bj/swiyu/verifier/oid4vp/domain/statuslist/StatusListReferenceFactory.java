package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntity;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.IssuerPublicKeyLoader;
import kotlin.NotImplementedError;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
@Slf4j
public class StatusListReferenceFactory {
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListResolverAdapter statusListResolverAdapter;

    /**
     * Creates a number of StatusListReferences according to the StatusLists found in the vcClaims
     * <p>
     * Claims may look for the Token Status List like
     * <pre><code>{
     *   "iss": "https://example.com",
     *   "status": {
     *     "status_list": {
     *       "idx": 0,
     *       "uri": "https://example.com/statuslists/1"
     *     }
     *   }
     * }</code></pre>
     * <p>
     * <p>
     * or the Bitstring Status List like
     * <pre><code>  "id": "https://example.com/credentials/23894672394",
     *   "type": ["VerifiableCredential", "EmployeeIdCredential"],
     *   "issuer": "did:example:12345",
     *   "validFrom": "2024-04-05T14:27:42Z",
     *   "credentialStatus": [{
     *     "id": "https://example.com/credentials/status/3#94567",
     *     "type": "BitstringStatusListEntry",
     *     "statusPurpose": "revocation",
     *     "statusListIndex": "94567",
     *     "statusListCredential": "https://example.com/credentials/status/3"
     *   }, {
     *     "id": "https://example.com/credentials/status/4#23452",
     *     "type": "BitstringStatusListEntry",
     *     "statusPurpose": "suspension",
     *     "statusListIndex": "23452",
     *     "statusListCredential": "https://example.com/credentials/status/4"
     *   }],
     *   "credentialSubject": {
     *     "id": "did:example:6789",
     *     "type": "Person",
     *     "employeeId": "A-123456"
     *   }</code></pre>
     *
     * @param vcClaims
     * @return a List of References, if no StatusListReference found in the claims, an empty list is returned
     */
    public List<StatusListReference> createStatusListReferences(Map<String, Object> vcClaims, ManagementEntity presentationManagementEntity) {
        List<StatusListReference> referenceList = new LinkedList<>();
        Optional.ofNullable(vcClaims.get("status"))
                .map(o -> (Map<String, Object>) o)
                .map(createTokenStatusListReferences(presentationManagementEntity))
                .ifPresent(referenceList::addAll);
        Optional.ofNullable(vcClaims.get("credentialStatus"))
                .map(createBitStringStatusListsHigherOrder(presentationManagementEntity))
                .ifPresent(referenceList::addAll);
        log.trace("Built {} StatusListReferences for fetching status lists for id {}", referenceList.size(), presentationManagementEntity.getId());
        return referenceList;
    }


    private Function<Map<String, Object>, List<TokenStatusListReference>> createTokenStatusListReferences(ManagementEntity presentationManagementEntity) {
        return tokenStatusListReferenceTokenEntry -> List.of(
                new TokenStatusListReference(statusListResolverAdapter, (Map<String, Object>) tokenStatusListReferenceTokenEntry.get("status_list"),
                        issuerPublicKeyLoader));
    }

    private Function<Object, List<StatusListReference>> createBitStringStatusListsHigherOrder(ManagementEntity presentationManagementEntity) {
        return bitStringEntries -> createBitStringStatusLists(bitStringEntries, presentationManagementEntity);
    }

    private List<StatusListReference> createBitStringStatusLists(Object w3cCredentialStatus, ManagementEntity presentationManagementEntity) {
        if (w3cCredentialStatus instanceof List) {
            return ((List<?>) w3cCredentialStatus).stream().map(createBitStringStatusListsHigherOrder(presentationManagementEntity)).flatMap(List::stream).collect(Collectors.toList());
        }
        // The implementation in similar fashion to the token status list would go here for bit string status list, should the need arise (again) to use these
        throw new NotImplementedError();
    }
}
