package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateGreenhouseRequest(

        @NotBlank String name,
        String description,

        @NotNull @Min(1)
        Integer bayCount,

        @NotNull @Min(0)
        Integer benchesPerBay,

        @NotNull @Min(1)
        Integer spotChecksPerBench,

        List<String> bayTags,
        List<String> benchTags,
        BigDecimal areaHectares
) {

    public CreateGreenhouseRequest(
            String name,
            String description,
            Integer bayCount,
            Integer benchesPerBay,
            Integer spotChecksPerBench,
            List<String> bayTags,
            List<String> benchTags
    ) {
        this(name, description, bayCount, benchesPerBay, spotChecksPerBench, bayTags, benchTags, null);
    }
}


