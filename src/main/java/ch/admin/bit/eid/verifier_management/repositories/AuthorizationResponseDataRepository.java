package ch.admin.bit.eid.verifier_management.repositories;

import ch.admin.bit.eid.verifier_management.models.AuthorizationResponseData;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface AuthorizationResponseDataRepository extends CrudRepository<AuthorizationResponseData, UUID> { }
