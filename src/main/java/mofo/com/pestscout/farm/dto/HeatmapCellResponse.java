package mofo.com.pestscout.farm.dto;

import lombok.Builder;
import mofo.com.pestscout.farm.model.SeverityLevel;

@Builder
public record HeatmapCellResponse(
        int bayIndex,
        int benchIndex,
        int pestCount,
        int diseaseCount,
        int beneficialCount,
        int totalCount,
        SeverityLevel severityLevel,
        String color
) {
}

