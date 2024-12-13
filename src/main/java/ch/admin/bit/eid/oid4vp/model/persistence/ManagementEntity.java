package ch.admin.bit.eid.oid4vp.model.persistence;

import ch.admin.bit.eid.oid4vp.model.converter.PresentationDefinitionConverter;
import ch.admin.bit.eid.oid4vp.model.converter.ResponseDataConverter;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

@Entity
@Table(name = "management")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagementEntity {

    @Id
    private UUID id;

    private String requestNonce;

    private VerificationStatusEnum state;

    @NotNull
    private Boolean jwtSecuredAuthorizationRequest;

    @Column(name = "requested_presentation", columnDefinition = "jsonb")
    @Convert(converter = PresentationDefinitionConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    private PresentationDefinition requestedPresentation;

    @Column(name = "wallet_response", columnDefinition = "jsonb")
    @Convert(converter = ResponseDataConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    private ResponseData walletResponse;

    private long expirationInSeconds;

    // Expiration time as unix epoch
    @Column(name = "expires_at")
    private long expiresAt;

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
