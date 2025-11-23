package mofo.com.pestscout.analytics.dto;

public record RecommendationDto(
        String scout,
        String location,
        String text,
        String priority,
        String status,
        String date
) {
}


