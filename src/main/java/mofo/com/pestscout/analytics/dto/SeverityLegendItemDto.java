package mofo.com.pestscout.analytics.dto;

public record SeverityLegendItemDto(
        String level,
        int minInclusive,
        int maxInclusive,
        String colorHex
) {
}

