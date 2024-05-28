package ch.admin.bit.eid.verifier_management.repositories;

import ch.admin.bit.eid.verifier_management.models.entities.PresentationDefinition;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PresentationDefinitionRepository extends CrudRepository<PresentationDefinition, String> { }
