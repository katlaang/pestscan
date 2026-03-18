package mofo.com.pestscout.farm.dto;

import java.util.List;

public record GreenhouseBayDto(
        int position,
        String bayTag,
        int bedCount,
        List<String> bedTags
) {
}
