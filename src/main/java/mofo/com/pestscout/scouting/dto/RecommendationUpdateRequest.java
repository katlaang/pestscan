package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.scouting.model.RecommendationType;

public record RecommendationUpdateRequest(
        @NotNull RecommendationType type,
        @NotNull String text
) {
}
