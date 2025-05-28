/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.domain.statuslist;

import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.json.JsonUtil;

import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Component
@AllArgsConstructor
@Slf4j
public class StatusListReferenceFactory {
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListResolverAdapter statusListResolverAdapter;
    private final VerificationProperties verificationProperties;

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
     * @param vcClaims The JSON claims of the verifiable credential as a Map
     * @return a List of References, if no StatusListReference found in the claims, an empty list is returned
     */
    public List<StatusListReference> createStatusListReferences(Map<String, Object> vcClaims, Management presentationManagement) {
        List<StatusListReference> referenceList = new LinkedList<>();
        var referencedTokenIssuer = Optional
                .ofNullable(vcClaims.get("iss"))
                .map(Object::toString)
                .orElseThrow();
        Optional.ofNullable(vcClaims.get("status"))
                .map(JsonUtil::getJsonObject)
                .map(createTokenStatusListReferences(referencedTokenIssuer))
                .ifPresent(referenceList::addAll);
        log.trace("Built {} StatusListReferences for fetching status lists for id {}", referenceList.size(), presentationManagement.getId());
        return referenceList;
    }


    private Function<Map<String, Object>, List<TokenStatusListReference>> createTokenStatusListReferences(String referencedTokenIssuer) {
        return tokenStatusListReferenceTokenEntry -> List.of(
                new TokenStatusListReference(statusListResolverAdapter, JsonUtil.getJsonObject(tokenStatusListReferenceTokenEntry.get("status_list")),
                        issuerPublicKeyLoader, referencedTokenIssuer, verificationProperties.getObjectSizeLimit())
        );
    }

}