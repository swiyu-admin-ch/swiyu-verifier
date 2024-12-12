package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.repositories.ManagementRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.integration.IntegrationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

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
