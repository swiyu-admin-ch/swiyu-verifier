package ch.admin.bj.swiyu.verifier.domain.ecosystem;

import ch.admin.bj.swiyu.verifier.domain.management.AuditMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.ToString;
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

    @JsonIgnore
    @ToString.Exclude
    @Column(nullable = true)
    String refreshToken;

    @JsonIgnore
    @ToString.Exclude
    @Column(nullable = false)
    String accessToken;

    @Column(nullable = false)
    Instant lastRefresh;

    /**
     * Updates this token set from the given OAuth2 token response.
     *
     * <p>The {@code refresh_token} is only overwritten when the provider supplies a non-null
     * value. Many OAuth2 servers omit the refresh token on {@code client_credentials} grants
     * or on rotation – nulling the field in those cases would erase a still-valid long-lived
     * credential and force the verifier to fall back permanently to {@code client_credentials}.</p>
     */
    public void apply(EcosystemApiType apiTarget, TokenApi.TokenResponse tokenResponse) {
        this.apiTarget = apiTarget;
        if (tokenResponse.refresh_token() != null) {
            this.refreshToken = tokenResponse.refresh_token();
        }
        this.accessToken = tokenResponse.access_token();
        this.lastRefresh = Instant.now();
    }
}
