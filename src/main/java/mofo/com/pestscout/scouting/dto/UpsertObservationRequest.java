package mofo.com.pestscout.scouting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.scouting.model.ObservationLifecycleStatus;
import mofo.com.pestscout.scouting.model.ObservationType;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(
        name = "UpsertObservationRequest",
        description = "Create or update one scouting observation. Supports species-based observations and type-only suspicious observations."
)
/**
 * Field-capture request for creating or updating a single scouting observation.
 */
public record UpsertObservationRequest(
        @Schema(description = "Optional echo of the parent scouting session id. When present it must match the path parameter.")
        UUID sessionId,
        @Schema(description = "Target greenhouse or field-block section inside the session. Required when the session has multiple sections.")
        UUID sessionTargetId,
        @Schema(description = "Built-in pest or disease code when the observed threat is already identified.")
        SpeciesCode speciesCode,
        @Schema(description = "Custom species id when the observed threat uses a farm-defined species catalogue entry.")
        UUID customSpeciesId,
        @NotNull
        @Schema(description = "Grid bay index for the observation location.", example = "1")
        Integer bayIndex,
        @Schema(description = "Optional human-readable bay label.", example = "Bay-1")
        String bayTag,
        @NotNull
        @Schema(description = "Grid bench or bed index for the observation location.", example = "1")
        Integer benchIndex,
        @Schema(description = "Optional human-readable bench or bed label.", example = "Bed-1")
        String benchTag,
        @Schema(description = "Optional sub-position index within a bench. Defaults to 1 when omitted.", example = "1")
        Integer spotIndex,
        @NotNull
        @Schema(description = "Observed count or severity tally at the selected grid position.", example = "4")
        Integer count,
        @Schema(description = "Optional scout notes for the observation.", example = "Localized thrips pressure near the vent row.")
        String notes,
        @Schema(description = "Optional idempotency key for offline-safe retries.")
        UUID clientRequestId,
        @Schema(description = "Optimistic-lock version when editing an existing observation.")
        Long version,
        @Schema(description = "Frontend-generated local observation id used to reconcile offline rows before server ids are known.", example = "LOCAL-OBS-001")
        String localObservationId,
        @Schema(description = "Explicit field observation intent. Can be sent even when the species is not yet identified.")
        ObservationType observationType,
        @Schema(description = "Workflow lifecycle for the observation. Separate from sync transport status.")
        ObservationLifecycleStatus lifecycleStatus,
        @Schema(description = "Per-observation latitude captured in the field. Must be sent together with longitude.", example = "1.2345678")
        BigDecimal latitude,
        @Schema(description = "Per-observation longitude captured in the field. Must be sent together with latitude.", example = "36.1234567")
        BigDecimal longitude,
        @Schema(description = "Optional point or polygon geometry payload, typically GeoJSON serialized as a string.", example = "{\"type\":\"Point\",\"coordinates\":[36.1234567,1.2345678]}")
        String geometry
) {
    /**
     * Backward-compatible constructor for older call sites that only send the core
     * grid/species/count fields.
     */
    public UpsertObservationRequest(
            UUID sessionId,
            UUID sessionTargetId,
            SpeciesCode speciesCode,
            Integer bayIndex,
            String bayTag,
            Integer benchIndex,
            String benchTag,
            Integer spotIndex,
            Integer count,
            String notes,
            UUID clientRequestId,
            Long version
    ) {
        this(
                sessionId,
                sessionTargetId,
                speciesCode,
                null,
                bayIndex,
                bayTag,
                benchIndex,
                benchTag,
                spotIndex,
                count,
                notes,
                clientRequestId,
                version,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Backward-compatible constructor for older call sites that include a custom
     * species id but do not yet send the enriched milestone 1 fields.
     */
    public UpsertObservationRequest(
            UUID sessionId,
            UUID sessionTargetId,
            SpeciesCode speciesCode,
            UUID customSpeciesId,
            Integer bayIndex,
            String bayTag,
            Integer benchIndex,
            String benchTag,
            Integer spotIndex,
            Integer count,
            String notes,
            UUID clientRequestId,
            Long version
    ) {
        this(
                sessionId,
                sessionTargetId,
                speciesCode,
                customSpeciesId,
                bayIndex,
                bayTag,
                benchIndex,
                benchTag,
                spotIndex,
                count,
                notes,
                clientRequestId,
                version,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}

