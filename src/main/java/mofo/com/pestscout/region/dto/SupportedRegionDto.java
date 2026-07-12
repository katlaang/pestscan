package mofo.com.pestscout.region.dto;

import java.util.List;

public record SupportedRegionDto(
        String country,
        List<String> states
) {
}
