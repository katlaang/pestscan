package mofo.com.pestscout.farm.dto;

import java.util.List;

public record FarmGridResponse(
        int bayCount,
        int benchesPerBay,
        List<HeatmapCellResponse> cells
) {
}

