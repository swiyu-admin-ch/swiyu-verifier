package ch.admin.bj.swiyu.verifier.infrastructure.scheduler;

import ch.admin.bj.swiyu.verifier.service.management.ManagementService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class ManagementCleanupScheduler {
    private final ManagementService managementService;

    @Scheduled(initialDelay = 0, fixedDelayString = "${application.data-clear-interval}")
    @SchedulerLock(name = "expireOffers")
    public void removeExpiredManagements() {
        log.info("Start scheduled removing of expired managements");
        managementService.removeExpiredManagements();
    }
}
