package mofo.com.pestscout.scouting.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(
        name = "BulkUpsertObservationsRequest",
        description = "Bulk wrapper for offline or grid-based observation upserts within one scouting session."
)
/**
 * Batch wrapper for multiple observation upserts in one session.
 */
public record BulkUpsertObservationsRequest(
        @Schema(description = "Optional echo of the parent scouting session id. When present it must match the path parameter.")
        UUID sessionId,
        @NotEmpty
        @ArraySchema(schema = @Schema(implementation = UpsertObservationRequest.class), minItems = 1)
        List<@Valid UpsertObservationRequest> observations
) {
}
