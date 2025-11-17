package mofo.com.pestscout.farm.dto;

import java.util.List;
import java.util.UUID;

public record HeatmapSectionResponse(
        UUID targetId,
        UUID greenhouseId,
        UUID fieldBlockId,
        String targetName,
        int bayCount,
        int benchesPerBay,
        List<HeatmapCellResponse> cells
) {
}
