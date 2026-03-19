package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record GreenhouseBayRequest(
        @NotBlank String bayTag,
        @Min(1) Integer bedCount,
        List<String> bedTags
) {
    public GreenhouseBayRequest(String bayTag, Integer bedCount) {
        this(bayTag, bedCount, null);
    }
}
