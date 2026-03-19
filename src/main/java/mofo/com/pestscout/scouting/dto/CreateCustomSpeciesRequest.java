package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.scouting.model.ObservationCategory;

import java.util.List;

public record CreateCustomSpeciesRequest(
        @NotNull ObservationCategory category,
        @NotEmpty List<String> names
) {
}
