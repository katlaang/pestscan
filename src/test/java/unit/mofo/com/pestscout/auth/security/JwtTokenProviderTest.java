package mofo.com.pestscout.auth.security;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    @Test
    void generateToken_containsUserDetailsAndValidates() {
        String secret = "very-secure-secret-key-should-be-long-123";
        JwtTokenProvider provider = new JwtTokenProvider(secret, 3600000, 7200000);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .role(Role.MANAGER)
                .build();

        String token = provider.generateToken(user);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(user.getId());
        assertThat(provider.getEmailFromToken(token)).isEqualTo(user.getEmail());
        assertThat(provider.getRoleFromToken(token)).isEqualTo(user.getRole().name());
    }
}
