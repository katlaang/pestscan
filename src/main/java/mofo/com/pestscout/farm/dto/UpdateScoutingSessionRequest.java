package mofo.com.pestscout.farm.dto;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Payload for updating an existing scouting session.
 * All fields are optional; only non-null values will be applied.
 * Manager may edit non-locked metadata.
 * */
public record UpdateScoutingSessionRequest(

        LocalDate sessionDate,
        Integer weekNumber,

        UUID greenhouseId,
        UUID fieldBlockId,

        String crop,
        String variety,

        BigDecimal temperatureCelsius,
        BigDecimal relativeHumidityPercent,
        LocalTime observationTime,
        String weatherNotes,
        String notes
) {
}

