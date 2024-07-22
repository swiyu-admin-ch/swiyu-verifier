package ch.admin.bit.eid.oid4vp.model.persistence;

import ch.admin.bit.eid.oid4vp.model.converter.PresentationDefinitionConverter;
import ch.admin.bit.eid.oid4vp.model.converter.ResponseDataConverter;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManagementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    private long expirationInSeconds;
}