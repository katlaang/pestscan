package mofo.com.pestscout.scouting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Manager creates a new scouting session for one or more greenhouses/fields.
 * Scout is taken from the farm's assigned scout and NOT passed from the client.
 */
public record CreateScoutingSessionRequest(

        @NotNull UUID farmId,

        @NotEmpty List<@Valid SessionTargetRequest> targets,

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




