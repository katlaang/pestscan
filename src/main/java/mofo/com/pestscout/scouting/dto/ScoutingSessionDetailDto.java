package mofo.com.pestscout.scouting.dto;

import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.model.SessionStatus;

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

        LocalDate sessionDate,
        Integer weekNumber,

        SessionStatus status,
        SyncStatus syncStatus,

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
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt,
        boolean confirmationAcknowledged,

        String reopenComment,

        List<ScoutingSessionSectionDto> sections,
        List<RecommendationEntryDto> recommendations
) {
}
