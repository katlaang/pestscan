package mofo.com.pestscout.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetUserPasswordRequest(
        @NotBlank(message = "Temporary password is required")
        @Size(min = 8, max = 255, message = "Temporary password must be between 8 and 255 characters")
        String temporaryPassword
) {
}
