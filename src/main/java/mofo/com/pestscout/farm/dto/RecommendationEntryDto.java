package mofo.com.pestscout.farm.dto;


import mofo.com.pestscout.farm.model.RecommendationType;

public record RecommendationEntryDto(
        RecommendationType type,
        String text
) {
}
