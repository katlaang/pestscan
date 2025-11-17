package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.farm.model.RecommendationType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Manager creates a new session for a farm, either greenhouse or field based.
 * Exactly one of greenhouseId or fieldBlockId should be non null.
 */
public record CreateScoutingSessionRequest(
        @NotNull UUID farmId,
        UUID greenhouseId,
        UUID fieldBlockId,
        @NotNull LocalDate sessionDate,
        @NotNull UUID managerId,
        UUID scoutId,
        String cropType,
        String cropVariety,
        String weather,
        String notes,
        Map<RecommendationType, String> recommendations
) {
}



