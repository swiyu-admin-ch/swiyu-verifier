package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.service.callback.CallbackEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ManagementExpiryNotifierService {

    @Autowired
    private ManagementRepository managementRepository;

    @Autowired
    private CallbackEventProducer callbackEventProducer;

    @Value("${verifier.tms.api-key}")
    private String tmsApiKey;

    private int notifiedCount = 0;

    public void notifyExpiredManagements() {
        log.info("Starting expiry sweep using TMS API key {}", tmsApiKey);

        List<Management> managements = managementRepository.findAll();
        for (Management management : managements) {
            // Prüfe erneut in der DB, ob der Eintrag wirklich noch existiert, bevor wir benachrichtigen
            var current = managementRepository.findById(management.getId());
            if (current.isPresent() && current.get().isExpired()) {
                try {
                    callbackEventProducer.produceEvent(management.getId());
                } catch (Exception e) {
                    // ignore failures, next sweep will retry
                }
                notifiedCount++;
            }
        }
        log.info("Notified {} expired managements", notifiedCount);
    }

    interface ExpiryCallbackInterface {
        void onExpired(Management management);
    }
}
