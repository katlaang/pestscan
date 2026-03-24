package mofo.com.pestscout.analytics.dto;

public record SeverityTrendPointDto(
        String week,
        int zero,
        int low,
        int medium,
        int high,
        int critical,
        Integer weekNumber,
        Integer year
) {
    public SeverityTrendPointDto(
            String week,
            int zero,
            int low,
            int medium,
            int high,
            int critical
    ) {
        this(week, zero, low, medium, high, critical, null, null);
    }
}
