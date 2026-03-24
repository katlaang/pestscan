package mofo.com.pestscout.farm.dto;

import java.math.BigDecimal;
import java.util.List;

public record FarmLayoutPreviewResponse(
        int greenhouseCount,
        int rows,
        int columns,
        boolean geoReferenced,
        String layoutMode,
        BigDecimal originLatitude,
        BigDecimal originLongitude,
        List<FarmLayoutStructureDto> greenhouses
) {
}
