package mofo.com.pestscout.scouting.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.farm.model.Farm;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_audit_events")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAuditEvent extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ScoutingSession session;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private SessionAuditAction action;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 50)
    private mofo.com.pestscout.auth.model.Role actorRole;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "device_type", length = 255)
    private String deviceType;

    @Column(name = "location", length = 512)
    private String location;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Override
    protected void applyPrePersistDefaults() {
        if (getOccurredAt() == null) {
            setOccurredAt(LocalDateTime.now());
        }
        if (getSyncStatus() == null) {
            setSyncStatus(SyncStatus.PENDING_UPLOAD);
        }
    }
}
