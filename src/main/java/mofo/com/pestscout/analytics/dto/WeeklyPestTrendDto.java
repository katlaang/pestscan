package mofo.com.pestscout.analytics.dto;

public record WeeklyPestTrendDto(
        String week,
        int thrips,
        int redSpider,
        int whiteflies,
        int mealybugs,
        int caterpillars,
        int fcm,
        int otherPests,
        Integer weekNumber,
        Integer year
) {
    public WeeklyPestTrendDto(
            String week,
            int thrips,
            int redSpider,
            int whiteflies,
            int mealybugs,
            int caterpillars,
            int fcm,
            int otherPests
    ) {
        this(week, thrips, redSpider, whiteflies, mealybugs, caterpillars, fcm, otherPests, null, null);
    }
}



