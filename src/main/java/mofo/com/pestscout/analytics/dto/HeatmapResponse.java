package mofo.com.pestscout.analytics.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * Heat map for one farm and one ISO week.
 * - "cells" is the farm-level overview grid (aggregated by bay and bench across all targets).
 * - "sections" contains separate grids for each greenhouse or field block.
 * - "severityLegend" tells the UI how to render colors and numeric ranges.
 */
@Builder
public record HeatmapResponse(
        UUID farmId,
        String farmName,
        int week,
        int year,

        // Farm level overview grid dimensions and cells
        int bayCount,
        int benchesPerBay,
        List<HeatmapCellResponse> cells,

        // Per section (per greenhouse or field block) heatmaps
        List<HeatmapSectionResponse> sections,

        // Legend entries for the Green to Dark Red scale
        List<SeverityLegendEntry> severityLegend
) {
}




