package mofo.com.pestscout.analytics.dto;

public record WeeklyPestTrendDto(
        String week,
        int thrips,
        int redSpider,
        int whiteflies,
        int mealybugs,
        int caterpillars,
        int fcm,
        int otherPests
) {
}



