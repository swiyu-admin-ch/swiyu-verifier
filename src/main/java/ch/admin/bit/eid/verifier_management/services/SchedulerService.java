package ch.admin.bit.eid.verifier_management.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class SchedulerService {
    private final ManagementService managementService;

    @Scheduled(initialDelay = 0, fixedDelayString = "${application.data-clear-interval}")
    @SchedulerLock(name = "expireOffers")
    public void removeExpiredManagements() {
        log.info("Start scheduled removing of expired managements");
        managementService.removeExpiredManagements();
        log.info("Finished scheduled removing of expired managements");
    }
}
