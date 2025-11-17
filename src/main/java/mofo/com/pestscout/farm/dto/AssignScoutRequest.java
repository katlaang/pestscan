package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignScoutRequest(
        @NotNull UUID scoutId
) {
}

