package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating an open-field block under a farm.
 * Farm id comes from the path variable in the URL.
 */
public record CreateFieldBlockRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @Min(0)
        Integer bayCount,

        @Min(0)
        Integer spotChecksPerBay,

        Boolean active
) {
}

