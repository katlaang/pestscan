package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.farm.model.ObservationCategory;
import mofo.com.pestscout.farm.model.SpeciesCode;

import java.util.UUID;

public record ScoutingObservationDto(
        UUID id,
        Long version,
        UUID sessionId,
        SpeciesCode speciesCode,
        ObservationCategory category,
        Integer bayIndex,
        Integer benchIndex,
        Integer spotIndex,
        Integer count,
        String notes
) {
}

