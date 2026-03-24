package mofo.com.pestscout.farm.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;

public record FarmLayoutPreviewRequest(
        @JsonDeserialize(using = LatitudeDeserializer.class)
        BigDecimal latitude,
        @JsonDeserialize(using = LongitudeDeserializer.class)
        BigDecimal longitude,
        @Min(1)
        Integer greenhouseCount,
        List<String> greenhouseNames
) {
}
