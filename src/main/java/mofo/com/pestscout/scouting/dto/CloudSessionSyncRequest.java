package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CloudSessionSyncRequest(
        @NotNull UUID farmId,
        @NotNull LocalDateTime since,
        boolean includeDeleted
) {
}

