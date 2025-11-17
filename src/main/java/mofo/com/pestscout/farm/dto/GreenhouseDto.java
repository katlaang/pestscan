package mofo.com.pestscout.farm.dto;

import java.util.UUID;

public record GreenhouseDto(
        UUID id,
        Long version,
        UUID farmId,
        String name,
        String description,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        Boolean active
) {
}
