package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.analytics.dto.HeatmapCellResponse;

import java.util.List;

public record FarmGridResponse(
        int bayCount,
        int benchesPerBay,
        List<HeatmapCellResponse> cells
) {
}

