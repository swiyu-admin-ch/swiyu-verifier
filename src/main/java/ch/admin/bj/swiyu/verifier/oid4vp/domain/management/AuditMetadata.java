package ch.admin.bj.swiyu.verifier.oid4vp.domain.management;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/**
 * Embeddable to provide auto tracking of audit data for an entity in the DB.
 */
@Embeddable
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC) // JPA
public class AuditMetadata {
    @Column
    @LastModifiedDate
    private Instant lastModifiedAt;

    @Column
    @LastModifiedBy
    private String lastModifiedBy;

    @Column
    @CreatedDate
    private Instant createdAt;

    @Column
    @CreatedBy
    private String createdBy;
}