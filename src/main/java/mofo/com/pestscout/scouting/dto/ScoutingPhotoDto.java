package mofo.com.pestscout.scouting.dto;

import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.model.PhotoSourceType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScoutingPhotoDto(
        UUID id,
        UUID sessionId,
        UUID observationId,
        UUID sessionTargetId,
        UUID farmId,
        Integer bayIndex,
        String bayTag,
        Integer benchIndex,
        String benchTag,
        Integer spotIndex,
        String localPhotoId,
        String purpose,
        String objectKey,
        PhotoSourceType sourceType,
        LocalDateTime capturedAt,
        LocalDateTime updatedAt,
        SyncStatus syncStatus
) {
}

