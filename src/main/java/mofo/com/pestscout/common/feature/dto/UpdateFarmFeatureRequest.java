package mofo.com.pestscout.common.feature.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateFarmFeatureRequest(
        @NotNull Boolean enabled
) {
}
