package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.util.UUID;

public record UpsertObservationRequest(
        @NotNull UUID sessionId,
        @NotNull UUID sessionTargetId,
        SpeciesCode speciesCode,
        UUID customSpeciesId,
        @NotNull Integer bayIndex,
        String bayTag,
        @NotNull Integer benchIndex,
        String benchTag,
        @NotNull Integer spotIndex,
        @NotNull Integer count,
        String notes,
        UUID clientRequestId,
        Long version    // can be null when inserting a new observation
) {
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
                version
        );
    }
}

