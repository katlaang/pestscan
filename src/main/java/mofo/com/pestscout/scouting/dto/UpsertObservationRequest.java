package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.util.UUID;

public record UpsertObservationRequest(
        @NotNull UUID sessionId,
        @NotNull UUID sessionTargetId,
        @NotNull SpeciesCode speciesCode,
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
}

