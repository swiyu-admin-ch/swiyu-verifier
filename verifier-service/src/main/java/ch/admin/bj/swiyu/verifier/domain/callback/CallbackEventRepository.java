package ch.admin.bj.swiyu.verifier.domain.callback;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;

public interface CallbackEventRepository extends JpaRepository<CallbackEvent, UUID> {

    /**
     * Fetch limit records, skipping locked ones
     * Using  <a href="https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html#locking-LockMode">UPGRADE_SKIPLOCKED from Hibernate User Guide</a>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(value = { @QueryHint(name="jakarta.persistence.lock.timeout", value = "-2")})
    @NonNull
    List<CallbackEvent> findAll();
}