package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.farm.model.SessionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Returned when a client loads a full scouting session.
 * Contains managerId and scoutId for UI dropdown mapping.
 */
public record ScoutingSessionDetailDto(
        UUID id,
        Long version,
        UUID farmId,
        UUID greenhouseId,
        UUID fieldBlockId,

        LocalDate sessionDate,
        Integer weekNumber,

        SessionStatus status,

        UUID managerId,
        UUID scoutId,

        String crop,
        String variety,

        // Weather
        BigDecimal temperatureCelsius,
        BigDecimal relativeHumidityPercent,
        LocalTime observationTime,
        String weatherNotes,

        // Session-level notes
        String notes,

        LocalDateTime startedAt,
        LocalDateTime completedAt,
        boolean confirmationAcknowledged,

        List<ScoutingObservationDto> observations,
        List<RecommendationEntryDto> recommendations
) {
}
