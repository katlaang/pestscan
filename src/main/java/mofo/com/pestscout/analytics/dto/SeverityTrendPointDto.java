package mofo.com.pestscout.analytics.dto;

public record SeverityTrendPointDto(
        String week,
        int zero,
        int low,
        int medium,
        int high,
        int critical
) {
}
