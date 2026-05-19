package ch.admin.bj.swiyu.verifier.service.oid4vp;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ch.admin.bj.swiyu.core.trust.client.api.TrustProtocol20Api;
import ch.admin.bj.swiyu.core.trust.client.model.PagedModelString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustStatementProvider {
    private final TrustProtocol20Api trustProtocol20Api;

    public Optional<String> getProtectedIssuanceTrustListStatement() {
        // TODO EIDOMNI-959 maybe do caching?
        return Optional.ofNullable(trustProtocol20Api.getActivePiTLS().block());
    }

    public List<String> getAllIssuanceStatementsFor(String issuerDid) {
        var responses = Mono.zip(
            trustProtocol20Api.getIdTS(issuerDid),
            trustProtocol20Api.getActivePiTLS(),
            trustProtocol20Api.getActiveNcTLS(),
            trustProtocol20Api.listPiaTS(issuerDid, true, null, null, null)
        ).block();

        List<String> statements = new LinkedList<>();
        Iterator<Object> it = responses.iterator();
        while(it.hasNext()) {
            Object response = it.next();
            if (response instanceof PagedModelString pageModelString) {
                statements.addAll(getListOfStatements(pageModelString));
            } 
            if (response instanceof String statement) {
                statements.add(statement);
            }
        }
        return statements;
    }


    private List<String> getListOfStatements(PagedModelString pagedModelString) {
        if (pagedModelString == null || pagedModelString.getContent() == null) {
            return List.of();
        }
        return pagedModelString.getContent();
    }
}
