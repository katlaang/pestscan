package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.farm.model.SpeciesCode;

import java.util.UUID;

public record UpsertObservationRequest(
        @NotNull UUID sessionId,
        @NotNull SpeciesCode speciesCode,
        @NotNull Integer bayIndex,
        @NotNull Integer benchIndex,
        @NotNull Integer spotIndex,
        @NotNull Integer count,
        String notes,
        Long version    // can be null when inserting a new observation
) {
}

