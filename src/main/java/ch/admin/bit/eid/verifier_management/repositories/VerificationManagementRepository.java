package ch.admin.bit.eid.verifier_management.repositories;

import ch.admin.bit.eid.verifier_management.models.entities.VerificationManagement;
import org.springframework.data.repository.CrudRepository;

public interface VerificationManagementRepository extends CrudRepository<VerificationManagement, String> {
}
