package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;

import java.util.List;

public record GreenhouseBayRequest(
        String bayTag,
        @Min(0) Integer bedCount,
        List<String> bedTags
) {
    public GreenhouseBayRequest(String bayTag, Integer bedCount) {
        this(bayTag, bedCount, null);
    }
}
