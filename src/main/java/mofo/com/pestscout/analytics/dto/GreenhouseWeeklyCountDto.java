package mofo.com.pestscout.analytics.dto;

import java.util.UUID;

public record GreenhouseWeeklyCountDto(
        UUID greenhouseId,
        String greenhouseName,
        int weekNumber,
        int year,
        String weekKey,
        String species,
        int totalCount
) {
}
