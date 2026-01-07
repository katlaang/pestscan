package mofo.com.pestscout.scouting.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scouting_photos", indexes = {
        @Index(name = "idx_photo_session", columnList = "session_id"),
        @Index(name = "idx_photo_local_id", columnList = "local_photo_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_photo_farm_local", columnNames = {"farm_id", "local_photo_id"})
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ScoutingPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ScoutingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observation_id")
    private ScoutingObservation observation;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "local_photo_id", nullable = false, length = 100)
    private String localPhotoId;

    @Column(name = "purpose", length = 255)
    private String purpose;

    @Column(name = "object_key", length = 500)
    private String objectKey;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;
}

