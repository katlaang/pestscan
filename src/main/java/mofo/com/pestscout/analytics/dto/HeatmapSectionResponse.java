package mofo.com.pestscout.analytics.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;


@Builder

public record HeatmapSectionResponse(
        UUID targetId,
        UUID greenhouseId,
        UUID fieldBlockId,
        String targetName,
        int bayCount,
        int benchesPerBay,
        List<HeatmapCellResponse> cells,
        List<HeatmapBayLayoutDto> bayLayouts
) {
    public HeatmapSectionResponse(
            UUID targetId,
            UUID greenhouseId,
            UUID fieldBlockId,
            String targetName,
            int bayCount,
            int benchesPerBay,
            List<HeatmapCellResponse> cells
    ) {
        this(targetId, greenhouseId, fieldBlockId, targetName, bayCount, benchesPerBay, cells, List.of());
    }
}


