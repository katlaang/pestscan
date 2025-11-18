package mofo.com.pestscout.analytics.dto;

import lombok.Builder;
import mofo.com.pestscout.scouting.model.SeverityLevel;

@Builder
public record HeatmapCellResponse(
        int bayIndex,
        int benchIndex,
        int pestCount,
        int diseaseCount,
        int beneficialCount,
        int totalCount,
        SeverityLevel severityLevel,
        String colorHex
) {
}


