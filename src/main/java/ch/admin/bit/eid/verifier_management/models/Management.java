package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.models.converters.PresentationDefinitionConverter;
import ch.admin.bit.eid.verifier_management.models.converters.ResponseDataConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "management")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Management implements Serializable {

    @Id
    private UUID id;

    private String requestNonce;

    private VerificationStatusEnum state;

    @Column(name = "requested_presentation", columnDefinition = "jsonb")
    @Convert(converter = PresentationDefinitionConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    private PresentationDefinition requestedPresentation;

    @Column(name = "wallet_response", columnDefinition = "jsonb")
    @Convert(converter = ResponseDataConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    private ResponseData walletResponse;

    @Column(name = "expiration_in_seconds")
    private long expirationInSeconds;
}
