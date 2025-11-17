package mofo.com.pestscout.farm.dto;


import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.farm.model.RecommendationType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Payload for updating an existing scouting session.
 * All fields are optional; only non-null values will be applied.
 */
public record UpdateScoutingSessionRequest(

        @NotNull
        UUID farmId,   // used to validate that the session stays in the same farm

        LocalDate sessionDate,
        String cropType,
        String cropVariety,
        String weather,
        String notes,

        // optional recommendation text per type
        Map<RecommendationType, String> recommendations,

        // optional reassignment of manager / scout
        UUID managerId,
        UUID scoutId
) {
}
