package mofo.com.pestscout.farm.dto;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;

public record UpdateGreenhouseRequest(

        String name,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        Boolean active,
        String description,
        java.util.List<String> bayTags,
        java.util.List<String> benchTags,
        BigDecimal areaHectares,
        @Valid List<GreenhouseBayRequest> bays
) {

    public UpdateGreenhouseRequest(
            String name,
            Integer bayCount,
            Integer benchesPerBay,
            Integer spotChecksPerBench,
            Boolean active,
            String description,
            java.util.List<String> bayTags,
            java.util.List<String> benchTags
    ) {
        this(name, bayCount, benchesPerBay, spotChecksPerBench, active, description, bayTags, benchTags, null, null);
    }

    public UpdateGreenhouseRequest(
            String name,
            Integer bayCount,
            Integer benchesPerBay,
            Integer spotChecksPerBench,
            Boolean active,
            String description,
            java.util.List<String> bayTags,
            java.util.List<String> benchTags,
            BigDecimal areaHectares
    ) {
        this(name, bayCount, benchesPerBay, spotChecksPerBench, active, description, bayTags, benchTags, areaHectares, null);
    }
}
