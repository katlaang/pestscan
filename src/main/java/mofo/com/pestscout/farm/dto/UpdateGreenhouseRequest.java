package mofo.com.pestscout.farm.dto;

import java.math.BigDecimal;

public record UpdateGreenhouseRequest(

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
        this(name, bayCount, benchesPerBay, spotChecksPerBench, active, description, bayTags, benchTags, null);
    }
}
