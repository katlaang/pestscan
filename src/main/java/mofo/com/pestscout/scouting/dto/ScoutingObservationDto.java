package mofo.com.pestscout.scouting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import mofo.com.pestscout.scouting.model.ObservationLifecycleStatus;
import mofo.com.pestscout.scouting.model.ObservationType;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(
        name = "ScoutingObservationDto",
        description = "Observation row returned in session detail, upsert responses, and sync payloads."
)
/**
 * Canonical observation payload returned to frontend, mobile, and sync clients.
 */
public record ScoutingObservationDto(
        @Schema(description = "Server-side observation id.")
        UUID id,
        @Schema(description = "Optimistic-lock version.")
        Long version,
        @Schema(description = "Parent scouting session id.")
        UUID sessionId,
        @Schema(description = "Target greenhouse or field-block section id.")
        UUID sessionTargetId,
        @Schema(description = "Greenhouse id when the observation belongs to a greenhouse target.")
        UUID greenhouseId,
        @Schema(description = "Field block id when the observation belongs to a field target.")
        UUID fieldBlockId,
        @Schema(description = "Built-in species code when the observation has been identified.")
        SpeciesCode speciesCode,
        @Schema(description = "Custom species id when the observation references a farm-defined species.")
        UUID customSpeciesId,
        @Schema(description = "Best display name for the observed threat or symptom.")
        String speciesDisplayName,
        @Schema(description = "Stable species or type identifier used for sync and deduplication.")
        String speciesIdentifier,
        @Schema(description = "Derived category for analytics and reporting.")
        ObservationCategory category,
        @Schema(description = "Grid bay index.", example = "1")
        Integer bayIndex,
        @Schema(description = "Human-readable bay label.", example = "Bay-1")
        String bayTag,
        @Schema(description = "Grid bench or bed index.", example = "1")
        Integer benchIndex,
        @Schema(description = "Human-readable bench or bed label.", example = "Bed-1")
        String benchTag,
        @Schema(description = "Sub-position index within a bench.", example = "1")
        Integer spotIndex,
        @Schema(description = "Observed count or tally.", example = "4")
        Integer count,
        @Schema(description = "Optional scout notes.")
        String notes,
        @Schema(description = "Last update timestamp on the server.")
        LocalDateTime updatedAt,
        @Schema(description = "Transport-level sync state.")
        SyncStatus syncStatus,
        @Schema(description = "Whether the observation is soft deleted in sync payloads.")
        boolean deleted,
        @Schema(description = "Soft-delete timestamp when the observation has been deleted.")
        LocalDateTime deletedAt,
        @Schema(description = "Optional idempotency key used by the client.")
        UUID clientRequestId,
        @Schema(description = "Frontend-generated local observation id used for offline reconciliation.", example = "LOCAL-OBS-001")
        String localObservationId,
        @Schema(description = "Explicit field observation intent.")
        ObservationType observationType,
        @Schema(description = "Observation workflow state.")
        ObservationLifecycleStatus lifecycleStatus,
        @Schema(description = "Per-observation latitude when captured.", example = "1.2345678")
        BigDecimal latitude,
        @Schema(description = "Per-observation longitude when captured.", example = "36.1234567")
        BigDecimal longitude,
        @Schema(description = "Optional serialized point or polygon geometry.")
        String geometry
) {
    /**
     * Backward-compatible constructor for older code paths that do not yet supply
     * the milestone 1 observation enrichment fields.
     */
    public ScoutingObservationDto(
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
        this(
                id,
                version,
                sessionId,
                sessionTargetId,
                greenhouseId,
                fieldBlockId,
                speciesCode,
                customSpeciesId,
                speciesDisplayName,
                speciesIdentifier,
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
                clientRequestId,
                null,
                category != null ? ObservationType.fromCategory(category) : null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Backward-compatible constructor for built-in species observations without
     * custom species metadata or milestone 1 enrichment fields.
     */
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
                clientRequestId,
                null,
                category != null ? ObservationType.fromCategory(category) : null,
                null,
                null,
                null,
                null
        );
    }
}

