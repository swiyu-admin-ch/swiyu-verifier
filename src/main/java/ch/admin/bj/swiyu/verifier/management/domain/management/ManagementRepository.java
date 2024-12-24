package ch.admin.bj.swiyu.verifier.management.domain.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ManagementRepository extends JpaRepository<Management, UUID> {
    @Transactional
    void deleteByExpiresAtIsBefore(Long expiresAt);
}
