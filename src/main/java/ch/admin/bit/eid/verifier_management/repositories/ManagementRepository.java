package ch.admin.bit.eid.verifier_management.repositories;

import ch.admin.bit.eid.verifier_management.models.Management;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ManagementRepository extends CrudRepository<Management, UUID> {
    @Transactional
    void deleteByExpiresAtIsBefore(Long expiresAt);
}
