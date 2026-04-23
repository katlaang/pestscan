package mofo.com.pestscout.scouting.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "ScoutingSyncResponse",
        description = "Delta payload returned by session sync endpoints for offline or edge clients."
)
/**
 * Sync response that bundles changed sessions and changed observations.
 */
public record ScoutingSyncResponse(
        @ArraySchema(schema = @Schema(implementation = ScoutingSessionDetailDto.class))
        List<ScoutingSessionDetailDto> sessions,
        @ArraySchema(schema = @Schema(implementation = ScoutingObservationDto.class))
        List<ScoutingObservationDto> observations
) {
}
