/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.domain.management;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.FAILED;

@Entity
@Table(
        name = "management",
        indexes = @Index(name = "idx_management_expires_at", columnList = "expires_at")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Management {

    @Embedded
    @Valid
    private final AuditMetadata auditMetadata = new AuditMetadata();

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID(); // Generate the ID manually

    @Builder.Default
    private String requestNonce = createNonce();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus state = VerificationStatus.PENDING;

    @NotNull
    @Builder.Default
    private Boolean jwtSecuredAuthorizationRequest = true;

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

    @Column(name = "accepted_issuer_dids")
    private List<String> acceptedIssuerDids;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trust_anchors")
    private List<TrustAnchor> trustAnchors;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="configuration_override", columnDefinition = "jsonb")
    private ConfigurationOverride configurationOverride = ConfigurationOverride.builder().build();

    @Column(name = "dcql_query", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private DcqlQuery dcqlQuery;

    @Builder.Default
    @Column(name = "response_specification", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @NotNull
    private ResponseSpecification responseSpecification = ResponseSpecification.builder().responseModeType(ResponseModeType.DIRECT_POST).build();

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();


    public boolean isVerificationPending() {
        return state == ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
    }

    public void verificationFailed(VerificationErrorResponseCode errorCode, String errorDescription) {
        this.state = FAILED;
        this.walletResponse = ResponseData.builder()
                .errorCode(errorCode)
                .errorDescription(errorDescription)
                .build();
    }

    public void verificationFailedDueToClientRejection(String errorDescription) {
        this.state = FAILED;
        this.walletResponse = ResponseData.builder()
                .errorCode(VerificationErrorResponseCode.CLIENT_REJECTED)
                .errorDescription(errorDescription)
                .build();
    }


    private static String createNonce() {
        final Base64.Encoder base64encoder = Base64.getEncoder().withoutPadding();

        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);

        return base64encoder.encodeToString(randomBytes);
    }

    private static long calculateExpiresAt(int expirationInSeconds) {
        return System.currentTimeMillis() + (expirationInSeconds * 1000L);
    }

    public void verificationSucceeded(String credentialSubjectData) {
        this.state = VerificationStatus.SUCCESS;
        this.walletResponse = ResponseData.builder()
                .credentialSubjectData(credentialSubjectData)
                .build();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Returns {@code true} if the verification process is still open
     * (not expired and still pending), {@code false} otherwise.
     */
    public boolean isProcessStillOpen() {
        return !isExpired() && isVerificationPending();
    }

    /**
     * Reset the timestamp at which this object will count as expired with the <code>expirationInSeconds</code>
     * @return this object for daisy chaining
     */
    public Management resetExpiresAt() {
        expiresAt = calculateExpiresAt(expirationInSeconds);
        return this;
    }

    @NotNull
    public ConfigurationOverride getConfigurationOverride() {
        return Objects.requireNonNullElseGet(this.configurationOverride, () -> ConfigurationOverride.builder().build());
    }
}
