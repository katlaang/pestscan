package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Payload for creating an open-field block under a farm.
 * Farm id comes from the path variable in the URL.
 * **/
//SUPER-ADMIN ONLY

public record CreateFieldBlockRequest(
        @NotBlank String name,
        @NotNull @Min(1)
        Integer bayCount,
        @NotNull @Min(1)
        Integer spotChecksPerBay,
        List<String> bayTags,
        Boolean active
) {
}
