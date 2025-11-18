package mofo.com.pestscout.analytics.dto;

import java.time.LocalDate;

public record TrendPointDto(
        LocalDate date,
        double severity
) {
}

