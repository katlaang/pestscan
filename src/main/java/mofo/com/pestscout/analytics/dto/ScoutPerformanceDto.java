package mofo.com.pestscout.analytics.dto;

public record ScoutPerformanceDto(
        String scout,
        int observations,
        int accuracy,
        String avgTime
) {
}

