package mofo.com.pestscout.scouting.dto;

import java.util.List;

public record ScoutingSyncResponse(
        List<ScoutingSessionDetailDto> sessions,
        List<ScoutingObservationDto> observations
) {
}
