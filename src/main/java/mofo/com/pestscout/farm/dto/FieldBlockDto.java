package mofo.com.pestscout.farm.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * Field block configuration for a farm.
 * Includes both raw values set on the block and the resolved values
 * after applying farm level defaults.
 */
@Builder

public record FieldBlockDto(
        UUID id,
        Long version,
        UUID farmId,
        String name,
        Integer bayCount,
        Integer spotChecksPerBay,
        List<String> bayTags,
        Boolean active
) {
}

