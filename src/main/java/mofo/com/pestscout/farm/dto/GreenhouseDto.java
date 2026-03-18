package mofo.com.pestscout.farm.dto;

import java.math.BigDecimal;
import java.util.List;
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
        List<String> bayTags,
        List<String> benchTags,
        Boolean active,
        BigDecimal areaHectares,
        List<GreenhouseBayDto> bays
) {

    public GreenhouseDto(
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
        this(id, version, farmId, name, description, bayCount, benchesPerBay, spotChecksPerBench, bayTags, benchTags, active, null, List.of());
    }

    public GreenhouseDto(
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
            Boolean active,
            BigDecimal areaHectares
    ) {
        this(id, version, farmId, name, description, bayCount, benchesPerBay, spotChecksPerBench, bayTags, benchTags, active, areaHectares, List.of());
    }
}
