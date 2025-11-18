package mofo.com.pestscout.scouting.dto;


import mofo.com.pestscout.analytics.dto.SessionTargetRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Payload for updating an existing scouting session.
 * All fields are optional; only non-null values will be applied.
 * Manager may edit non-locked metadata.
 * */
public record UpdateScoutingSessionRequest(

        LocalDate sessionDate,
        Integer weekNumber,

        List<SessionTargetRequest> targets,

        String crop,
        String variety,

        BigDecimal temperatureCelsius,
        BigDecimal relativeHumidityPercent,
        LocalTime observationTime,
        String weatherNotes,
        String notes
) {
}

