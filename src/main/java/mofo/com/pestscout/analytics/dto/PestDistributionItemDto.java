package mofo.com.pestscout.analytics.dto;

public record PestDistributionItemDto(
        String name,
        int value,
        double percentage,
        String severity
) {
}
