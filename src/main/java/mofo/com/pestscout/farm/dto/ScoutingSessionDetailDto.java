package mofo.com.pestscout.farm.dto;


import mofo.com.pestscout.farm.model.SessionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ScoutingSessionDetailDto(
        UUID id,
        Long version,
        UUID farmId,
        UUID greenhouseId,
        UUID fieldBlockId,
        LocalDate sessionDate,
        SessionStatus status,
        UUID managerId,
        UUID scoutId,
        String cropType,
        String cropVariety,
        String weather,
        String notes,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        boolean confirmationAcknowledged,
        List<ScoutingObservationDto> observations,
        List<RecommendationEntryDto> recommendations
) {
}
