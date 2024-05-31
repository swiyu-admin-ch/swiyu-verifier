package ch.admin.bit.eid.oid4vp.repository;

import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import org.springframework.data.repository.CrudRepository;

public interface VerificationManagementRepository extends CrudRepository<ManagementEntity, String> {
}
