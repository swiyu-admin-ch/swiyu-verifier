package ch.admin.bj.swiyu.verifier.management.domain.management;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;

@Entity
@Table(
        name = "management",
        indexes = @Index(name = "idx_management_expires_at", columnList = "expires_at")
)
@Getter
@NoArgsConstructor // JPA
public class Management {

    @Id
    private UUID id;

    private String requestNonce;

    @Enumerated(EnumType.STRING)
    private VerificationStatus state;

    @NotNull
    private Boolean jwtSecuredAuthorizationRequest;

    @Column(name = "requested_presentation", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private PresentationDefinition requestedPresentation;

    @Column(name = "wallet_response", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private ResponseData walletResponse;

    @Column(name = "expiration_in_seconds")
    private int expirationInSeconds;

    // Expiration time as unix epoch
    @Column(name = "expires_at")
    private long expiresAt;

    // Comma-separated list of accepted issuer DIDs.
    // Supports both Postgres and H2. Since H2 lacks text[] type support, we choose simplicity
    // over external libraries (e.g., vladmihalcea) and use a comma-separated list instead.
    @Column(name = "accepted_issuer_dids")
    private String acceptedIssuerDids;

    public Management(UUID id, int expirationInSeconds, PresentationDefinition requestedPresentation, boolean jwtSecuredAuthorizationRequest, List<String> acceptedIssuerDids) {
        this.id = id;
        this.state = VerificationStatus.PENDING;
        this.requestNonce = createNonce();
        this.expirationInSeconds = expirationInSeconds;
        this.expiresAt = calculateExpiresAt(expirationInSeconds);
        this.requestedPresentation = requestedPresentation;
        this.jwtSecuredAuthorizationRequest = jwtSecuredAuthorizationRequest;
        this.acceptedIssuerDids = convertToComaSeperatedList(acceptedIssuerDids);
    }

    public List<String> getAcceptedIssuerDids() {
        if (this.acceptedIssuerDids != null && !this.acceptedIssuerDids.isBlank()) {
            return List.of(this.acceptedIssuerDids.split(","));
        } else {
            return emptyList();
        }
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    private static String createNonce() {
        final SecureRandom random = new SecureRandom();
        final Base64.Encoder base64encoder = Base64.getEncoder().withoutPadding();

        byte[] randomBytes = new byte[24];
        random.nextBytes(randomBytes);

        return base64encoder.encodeToString(randomBytes);
    }

    private static long calculateExpiresAt(int expirationInSeconds) {
        return System.currentTimeMillis() + (expirationInSeconds * 1000L);
    }

    private String convertToComaSeperatedList(List<String> allowedIssuerDids) {
        if (allowedIssuerDids == null || allowedIssuerDids.isEmpty()) {
            return "";
        }
        return String.join(",", allowedIssuerDids);
    }
}
