package mofo.com.pestscout.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DiseaseDistributionItemDto(
        String name,
        int value,
        double percentage,
        String severity
) {
    @JsonProperty("count")
    public int count() {
        return value;
    }
}

