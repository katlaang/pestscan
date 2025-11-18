package mofo.com.pestscout.farm.dto;

import java.util.UUID;
import java.util.List;

public record GreenhouseDto(
        UUID id,
        Long version,
        UUID farmId,
        String name,
        String description,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        List<String> bayTags,
        List<String> benchTags,
        Boolean active
) {
}
