package mofo.com.pestscout.scouting.dto;

import mofo.com.pestscout.common.model.SyncStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScoutingPhotoDto(
        UUID id,
        UUID sessionId,
        UUID observationId,
        UUID farmId,
        String localPhotoId,
        String purpose,
        String objectKey,
        LocalDateTime capturedAt,
        LocalDateTime updatedAt,
        SyncStatus syncStatus
) {
}

