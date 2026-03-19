package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.auth.model.Role;

import java.util.UUID;

public record FarmMemberAssignmentRequest(
        @NotNull UUID userId,
        @NotNull Role role
) {
}
