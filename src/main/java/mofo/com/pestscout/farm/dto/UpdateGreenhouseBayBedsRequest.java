package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateGreenhouseBayBedsRequest(
        @NotNull
        @Min(0)
        Integer bedCount
) {
}
