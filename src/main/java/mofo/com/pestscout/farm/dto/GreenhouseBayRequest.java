package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GreenhouseBayRequest(
        @NotBlank String bayTag,
        @Min(1) Integer bedCount
) {
}
