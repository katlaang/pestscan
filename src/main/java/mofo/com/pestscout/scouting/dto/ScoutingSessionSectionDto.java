package mofo.com.pestscout.scouting.dto;

import java.util.List;
import java.util.UUID;

/**
 * Each section represents a greenhouse or field block included in the session.
 * Observations are grouped under the target to keep the UI grid aligned with the
 * dropdown selections.
 */
public record ScoutingSessionSectionDto(
        UUID targetId,
        UUID greenhouseId,
        UUID fieldBlockId,
        Boolean includeAllBays,
        Boolean includeAllBenches,
        List<String> bayTags,
        List<String> benchTags,
        List<ScoutingObservationDto> observations
) {
}
