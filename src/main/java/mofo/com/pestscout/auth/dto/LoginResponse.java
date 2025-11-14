package mofo.com.pestscout.auth.dto;

import lombok.Builder;

@Builder
public record LoginResponse(
        String token,
        String refreshToken,
        long expiresIn,
        UserDto user
) {
}
