package mofo.com.pestscout.scouting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(
        name = "CloudSessionSyncRequest",
        description = "Edge-to-cloud delta sync request for scouting sessions and observations."
)
/**
 * Edge sync request that asks for session and observation deltas after a timestamp.
 */
public record CloudSessionSyncRequest(
        @NotNull
        @Schema(description = "Farm id to synchronize.")
        UUID farmId,
        @NotNull
        @Schema(description = "Return records changed after this timestamp.", example = "2026-04-22T12:00:00")
        LocalDateTime since,
        @Schema(description = "Include soft-deleted observations in the delta payload.")
        boolean includeDeleted
) {
}

