package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Partial update for a field block.
 * All fields are optional; null means "no change".
 */
public record UpdateFieldBlockRequest(
        @Size(max = 255)
        String name,

        @Min(0)
        Integer bayCount,

        @Min(0)
        Integer spotChecksPerBay,

        java.util.List<String> bayTags,

        Boolean active,

        BigDecimal areaHectares
) {

    public UpdateFieldBlockRequest(
            String name,
            Integer bayCount,
            Integer spotChecksPerBay,
            java.util.List<String> bayTags,
            Boolean active
    ) {
        this(name, bayCount, spotChecksPerBay, bayTags, active, null);
    }
}



