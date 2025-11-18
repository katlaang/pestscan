package mofo.com.pestscout.scouting.dto;

import mofo.com.pestscout.scouting.model.SessionStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ScoutingSessionSummaryDto(
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
        String cropVariety
) {
}


