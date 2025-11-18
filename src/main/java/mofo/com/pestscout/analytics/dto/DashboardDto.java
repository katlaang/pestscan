package mofo.com.pestscout.analytics.dto;

import java.util.List;

public record DashboardDto(
        DashboardSummaryDto summary,
        List<PestDistributionItemDto> pestDistribution,
        List<DiseaseDistributionItemDto> diseaseDistribution,
        List<WeeklyPestTrendDto> weeklyTrends,
        List<SeverityTrendPointDto> severityTrend,
        List<HeatmapCellResponse> heatmap,
        List<AlertDto> alerts,
        List<RecommendationDto> recommendations,
        List<FarmComparisonDto> farmComparison,
        List<ScoutPerformanceDto> scoutPerformance
) {
}

