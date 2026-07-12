package mofo.com.pestscout.auth.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAlertCuratorPermissionRequest(
        @NotNull Boolean enabled
) {
}
