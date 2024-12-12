package ch.admin.bit.eid.verifier_management.repositories;

import ch.admin.bit.eid.verifier_management.models.Management;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ManagementRepository extends CrudRepository<Management, UUID> {
    @Transactional
    void deleteByExpiresAtIsBefore(Long expiresAt);
}
