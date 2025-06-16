package ch.admin.bj.swiyu.verifier.domain.callback;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC) // JPA
@Table(name="callback_event")
public class CallbackEvent {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID(); // Generate the ID manually

    /**
     * ID of the verification request
     */
    @Column
    private UUID verificationId;

    @Column
    @CreatedDate
    private Instant timestamp;


}