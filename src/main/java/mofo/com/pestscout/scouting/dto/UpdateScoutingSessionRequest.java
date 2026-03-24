package mofo.com.pestscout.scouting.dto;


import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.scouting.model.PhotoSourceType;
import mofo.com.pestscout.scouting.model.SessionStatus;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload for updating an existing scouting session.
 * All fields are optional; only non-null values will be applied.
 * Manager may edit non-locked metadata, including the assigned scout while a session is still in planning.
 * */
public record UpdateScoutingSessionRequest(

        LocalDate sessionDate,
        Integer weekNumber,

        List<SessionTargetRequest> targets,
        UUID scoutId,

        String crop,
        String variety,

        BigDecimal temperatureCelsius,
        BigDecimal relativeHumidityPercent,
        LocalTime observationTime,
        String weatherNotes,
        String notes,
        List<SpeciesCode> surveySpeciesCodes,
        List<UUID> customSurveySpeciesIds,
        PhotoSourceType defaultPhotoSourceType,
        SessionStatus status,
        Long version,
        String deviceId,
        String deviceType,
        String location,
        String comment,
        String actorName,
        String observationTimezone
) {
    public UpdateScoutingSessionRequest(
            LocalDate sessionDate,
            Integer weekNumber,
            List<SessionTargetRequest> targets,
            UUID scoutId,
            String crop,
            String variety,
            BigDecimal temperatureCelsius,
            BigDecimal relativeHumidityPercent,
            LocalTime observationTime,
            String weatherNotes,
            String notes,
            PhotoSourceType defaultPhotoSourceType,
            SessionStatus status,
            Long version,
            String deviceId,
            String deviceType,
            String location,
            String comment,
            String actorName
    ) {
        this(
                sessionDate,
                weekNumber,
                targets,
                scoutId,
                crop,
                variety,
                temperatureCelsius,
                relativeHumidityPercent,
                observationTime,
                weatherNotes,
                notes,
                null,
                null,
                defaultPhotoSourceType,
                status,
                version,
                deviceId,
                deviceType,
                location,
                comment,
                actorName,
                null
        );
    }

    public UpdateScoutingSessionRequest(
            LocalDate sessionDate,
            Integer weekNumber,
            List<SessionTargetRequest> targets,
            UUID scoutId,
            String crop,
            String variety,
            BigDecimal temperatureCelsius,
            BigDecimal relativeHumidityPercent,
            LocalTime observationTime,
            String weatherNotes,
            String notes,
            List<SpeciesCode> surveySpeciesCodes,
            List<UUID> customSurveySpeciesIds,
            PhotoSourceType defaultPhotoSourceType,
            SessionStatus status,
            Long version,
            String deviceId,
            String deviceType,
            String location,
            String comment,
            String actorName
    ) {
        this(
                sessionDate,
                weekNumber,
                targets,
                scoutId,
                crop,
                variety,
                temperatureCelsius,
                relativeHumidityPercent,
                observationTime,
                weatherNotes,
                notes,
                surveySpeciesCodes,
                customSurveySpeciesIds,
                defaultPhotoSourceType,
                status,
                version,
                deviceId,
                deviceType,
                location,
                comment,
                actorName,
                null
        );
    }
}
