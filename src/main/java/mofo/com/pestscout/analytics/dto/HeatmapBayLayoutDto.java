package mofo.com.pestscout.analytics.dto;

import java.util.List;

public record HeatmapBayLayoutDto(
        int bayIndex,
        String bayTag,
        int bedCount,
        List<String> bedTags
) {
}
