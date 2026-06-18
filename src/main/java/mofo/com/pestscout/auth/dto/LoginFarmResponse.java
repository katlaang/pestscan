package mofo.com.pestscout.auth.dto;

import mofo.com.pestscout.auth.model.Role;

import java.util.UUID;

public record LoginFarmResponse(
        UUID farmId,
        String slug,
        String name,
        Role role
) {
}
