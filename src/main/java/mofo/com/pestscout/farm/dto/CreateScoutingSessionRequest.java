package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Manager creates a new scouting session for a specific greenhouse or field block.
 * Exactly one of greenhouseId or fieldBlockId must be provided.
 * Scout is taken from the farm's assigned scout and NOT passed from the client.
 */
public record CreateScoutingSessionRequest(

        @NotNull UUID farmId,

        // Structure: exactly one of these should be non-null
        UUID greenhouseId,
        UUID fieldBlockId,

        @NotNull LocalDate sessionDate,

        // Week number shown on the paper sheet
        Integer weekNumber,

        // Crop + variety at the top of the sheet
        String crop,
        String variety,

        // Weather section
        BigDecimal temperatureCelsius,       // Temp____
        BigDecimal relativeHumidityPercent,  // RH____
        LocalTime observationTime,           // Time____

        // Free-text weather summary
        String weatherNotes,

        // General remarks for the whole session (not per bay)
        String notes
) {
}




