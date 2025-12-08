package ch.admin.bj.swiyu.verifier.service.oid4vp.ports;

import java.util.UUID;

/**
 * Port: notifies business systems about verification completion.
 */
public interface WebhookNotifier {
    void produceEvent(UUID managementEntityId);
}
