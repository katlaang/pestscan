package mofo.com.pestscout.scouting.dto;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.model.SessionAuditAction;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScoutingSessionAuditDto(
        UUID id,
        UUID sessionId,
        SessionAuditAction action,
        UUID actorId,
        String actorName,
        String actorEmail,
        Role actorRole,
        String deviceId,
        String deviceType,
        String location,
        String comment,
        LocalDateTime occurredAt,
        SyncStatus syncStatus
) {
}
