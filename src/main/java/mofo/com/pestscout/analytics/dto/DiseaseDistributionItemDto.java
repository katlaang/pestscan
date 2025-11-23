package mofo.com.pestscout.analytics.dto;

public record DiseaseDistributionItemDto(
        String name,
        int value,
        double percentage,
        String severity
) {
}

