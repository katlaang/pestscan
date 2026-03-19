package mofo.com.pestscout.scouting.dto;

import mofo.com.pestscout.scouting.model.ObservationCategory;

import java.util.UUID;

public record CustomSpeciesDto(
        UUID id,
        ObservationCategory category,
        String name,
        String code
) {
}
