package mofo.com.pestscout.analytics.dto;

import java.util.List;
import java.util.UUID;

public record PestTrendResponse(
        UUID farmId,
        String speciesCode,
        List<TrendPointDto> points
) {
}

