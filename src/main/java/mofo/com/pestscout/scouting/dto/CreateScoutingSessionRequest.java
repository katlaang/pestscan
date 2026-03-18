package mofo.com.pestscout.scouting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.scouting.model.PhotoSourceType;
import mofo.com.pestscout.scouting.model.SessionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin roles create a new scouting session for a farm.
 * Scout assignment and structure targeting may be provided up front, but they are
 * optional so the client can create draft sessions first and fill in details later.
 */
public record CreateScoutingSessionRequest(

        @NotNull UUID farmId,

        UUID scoutId,

        @Valid List<SessionTargetRequest> targets,

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
        String notes,

        // Default capture source for photos in this session; photo registration may override it
        PhotoSourceType defaultPhotoSourceType,

        // Optional initial lifecycle state. Incomplete plans remain DRAFT; complete plans are promoted to NEW.
        SessionStatus status,

        // Optional audit metadata
        String deviceId,
        String deviceType,
        String location,
        String comment,
        String actorName
) {
}



