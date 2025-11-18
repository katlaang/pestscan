package mofo.com.pestscout.analytics.dto;

public record FarmComparisonDto(
        String farm,
        double avgSeverity,
        int observations,
        int alerts
) {
}

