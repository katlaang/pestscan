package mofo.com.pestscout.farm.dto;

import java.util.List;

public record FarmLayoutStructureDto(
        String name,
        int orderIndex,
        Double centerLatitude,
        Double centerLongitude,
        List<List<Double>> polygon
) {
}
