package mofo.com.pestscout.scouting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkUpsertObservationsRequest(
        @NotNull UUID sessionId,
        @NotEmpty List<@Valid UpsertObservationRequest> observations
) {
}
