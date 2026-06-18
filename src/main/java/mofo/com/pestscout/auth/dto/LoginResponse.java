package mofo.com.pestscout.auth.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record LoginResponse(
        String token,
        String refreshToken,
        long expiresIn,
        UserDto user,
        List<LoginFarmResponse> farms,
        String clientSessionId
) {
    public LoginResponse {
        farms = farms == null ? List.of() : List.copyOf(farms);
    }
}
