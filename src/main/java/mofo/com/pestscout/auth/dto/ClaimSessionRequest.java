package mofo.com.pestscout.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimSessionRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
