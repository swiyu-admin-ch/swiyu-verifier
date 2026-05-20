package ch.admin.bj.swiyu.verifier.domain.ecosystem;

import ch.admin.bj.swiyu.verifier.domain.management.AuditMetadata;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import lombok.Getter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Entity to manage the swiyu provider provided tokens.
 * <warning>No instance of this class should not be accessed outside the
 * TokenManager class.</warning>
 */

@Entity
@Getter
@Table(name = "token_set")
@EntityListeners(AuditingEntityListener.class)
public class TokenSet {

    @Embedded
    @Valid
    private final AuditMetadata auditMetadata = new AuditMetadata();

    @Id
    @Enumerated(EnumType.STRING)
    EcosystemApiType apiTarget;

    @Column(nullable = true)
    String refreshToken;

    @Column(nullable = false)
    String accessToken;

    @Column(nullable = false)
    Instant lastRefresh;

    public void apply(EcosystemApiType apiTarget, TokenApi.TokenResponse tokenResponse) {
        this.apiTarget = apiTarget;
        this.refreshToken = tokenResponse.refresh_token();
        this.accessToken = tokenResponse.access_token();
        this.lastRefresh = Instant.now();
    }
}
