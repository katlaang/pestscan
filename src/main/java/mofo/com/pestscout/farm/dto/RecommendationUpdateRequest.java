package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.farm.model.RecommendationType;

public record RecommendationUpdateRequest(
        @NotNull RecommendationType type,
        @NotNull String text
) {
}
