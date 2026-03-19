package mofo.com.pestscout.scouting.dto;

import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScoutingObservationDto(
        UUID id,
        Long version,
        UUID sessionId,
        UUID sessionTargetId,
        UUID greenhouseId,
        UUID fieldBlockId,
        SpeciesCode speciesCode,
        UUID customSpeciesId,
        String speciesDisplayName,
        String speciesIdentifier,
        ObservationCategory category,
        Integer bayIndex,
        String bayTag,
        Integer benchIndex,
        String benchTag,
        Integer spotIndex,
        Integer count,
        String notes,
        LocalDateTime updatedAt,
        SyncStatus syncStatus,
        boolean deleted,
        LocalDateTime deletedAt,
        UUID clientRequestId
) {
    public ScoutingObservationDto(
            UUID id,
            Long version,
            UUID sessionId,
            UUID sessionTargetId,
            UUID greenhouseId,
            UUID fieldBlockId,
            SpeciesCode speciesCode,
            ObservationCategory category,
            Integer bayIndex,
            String bayTag,
            Integer benchIndex,
            String benchTag,
            Integer spotIndex,
            Integer count,
            String notes,
            LocalDateTime updatedAt,
            SyncStatus syncStatus,
            boolean deleted,
            LocalDateTime deletedAt,
            UUID clientRequestId
    ) {
        this(
                id,
                version,
                sessionId,
                sessionTargetId,
                greenhouseId,
                fieldBlockId,
                speciesCode,
                null,
                speciesCode != null ? speciesCode.getDisplayName() : null,
                speciesCode != null ? "CODE:" + speciesCode.name() : null,
                category,
                bayIndex,
                bayTag,
                benchIndex,
                benchTag,
                spotIndex,
                count,
                notes,
                updatedAt,
                syncStatus,
                deleted,
                deletedAt,
                clientRequestId
        );
    }
}

