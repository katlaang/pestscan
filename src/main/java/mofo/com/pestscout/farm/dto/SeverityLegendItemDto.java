package mofo.com.pestscout.farm.dto;

public record SeverityLegendItemDto(
        String level,
        int minInclusive,
        int maxInclusive,
        String colorHex
) {
}

