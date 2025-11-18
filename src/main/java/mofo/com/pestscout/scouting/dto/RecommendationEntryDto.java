package mofo.com.pestscout.scouting.dto;


import mofo.com.pestscout.scouting.model.RecommendationType;

public record RecommendationEntryDto(
        RecommendationType type,
        String text
) {
}
