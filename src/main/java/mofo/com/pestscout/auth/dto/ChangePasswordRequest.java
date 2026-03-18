package mofo.com.pestscout.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @JsonAlias({"oldPassword"})
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @JsonAlias({"password"})
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
        String newPassword
) {
}
