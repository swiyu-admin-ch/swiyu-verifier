package ch.admin.bit.eid.oid4vp.repository;

import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PresentationDefinitionRepository extends CrudRepository<PresentationDefinition, UUID> { }
