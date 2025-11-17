package mofo.com.pestscout.farm.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record HeatmapResponse(
        UUID farmId,
        String farmName,
        int week,
        int year,
        int bayCount,
        int benchesPerBay,
        List<HeatmapCellResponse> cells,
        Map<String, String> severityLegend
) {
}

