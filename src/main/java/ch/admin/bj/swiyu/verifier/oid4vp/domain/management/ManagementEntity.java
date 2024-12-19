package ch.admin.bj.swiyu.verifier.oid4vp.domain.management;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationError;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationErrorResponseCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "management")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagementEntity {

    @Id
    private UUID id;

    private String requestNonce;

    // Note: we store the enum as ordinal but as string in the database, this is a techdebt
    // and should be EnumType.STRING
    @Column(columnDefinition = "text")
    @Enumerated(EnumType.ORDINAL)
    private VerificationStatus state;

    @NotNull
    private Boolean jwtSecuredAuthorizationRequest;

    @Column(name = "requested_presentation", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private PresentationDefinition requestedPresentation;

    @Column(name = "wallet_response", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private ResponseData walletResponse;

    private int expirationInSeconds;

    /**
     * Expiration time as unix epoch
     */
    @Column(name = "expires_at")
    private long expiresAt;

    public boolean isVerificationPending() {
        return state == VerificationStatus.PENDING;
    }

    public void verificationFailed(VerificationError type, VerificationErrorResponseCode errorCode) {
        if (type == VerificationError.VERIFICATION_PROCESS_CLOSED) {
            // edge case: when this error type occurs, the entity is already in a Non-PENDING state
            // so we keep it in the current state
            return;
        }
        this.state = VerificationStatus.FAILED;
        this.walletResponse = ResponseData.builder()
                .errorCode(errorCode)
                .build();
    }

    public void verificationFailedDueToClientRejection(String errorDescription) {
        this.state = VerificationStatus.FAILED;
        this.walletResponse = ResponseData.builder()
                .errorCode(VerificationErrorResponseCode.CLIENT_REJECTED)
                .errorDescription(errorDescription)
                .build();
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
}
