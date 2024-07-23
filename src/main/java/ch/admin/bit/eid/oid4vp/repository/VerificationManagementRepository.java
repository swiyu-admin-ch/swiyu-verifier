package ch.admin.bit.eid.oid4vp.repository;

import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VerificationManagementRepository extends JpaRepository<ManagementEntity, UUID> {
}
