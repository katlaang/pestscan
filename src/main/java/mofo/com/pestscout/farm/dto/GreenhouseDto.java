package mofo.com.pestscout.farm.dto;

import lombok.Builder;

import java.util.UUID;


@Builder
public record GreenhouseDto(
        UUID id,
        UUID farmId,
        String name,
        String description,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        Integer resolvedBayCount,
        Integer resolvedBenchesPerBay,
        Integer resolvedSpotChecksPerBench,
        Boolean active
) {
}




