package ch.admin.bj.swiyu.verifier.management.service;

import lombok.AllArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SchedulerService {
    private final ManagementService managementService;

    @Scheduled(initialDelay = 0, fixedDelayString = "${application.data-clear-interval}")
    @SchedulerLock(name = "expireOffers")
    public void removeExpiredManagements() {
        managementService.removeExpiredManagements();
    }
}
