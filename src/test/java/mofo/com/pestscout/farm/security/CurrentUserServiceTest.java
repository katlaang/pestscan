package mofo.com.pestscout.farm.security;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserService currentUserService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsUserFromRepository() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("scout@example.com")
                .password("pw")
                .role(Role.SCOUT)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword(), List.of())
        );

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThat(currentUserService.getCurrentUser()).isEqualTo(user);
    }

    @Test
    void getCurrentUser_throwsWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(currentUserService::getCurrentUser)
                .isInstanceOf(UnauthorizedException.class);
    }
}
