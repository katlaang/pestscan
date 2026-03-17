package mofo.com.pestscout.auth.service;

import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSessionService userSessionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userSessionService, "activityWriteThrottleSeconds", 30L);
    }

    @Test
    void recordActivity_updatesLastActivityWithBulkUpdateWhenStale() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .lastActivityAt(LocalDateTime.now().minusMinutes(1))
                .build();
        LocalDateTime previousActivityAt = user.getLastActivityAt();

        userSessionService.recordActivity(user);

        verify(userRepository).updateLastActivityAtIfStale(eq(user.getId()), any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(user.getLastActivityAt()).isAfter(previousActivityAt);
    }

    @Test
    void recordActivity_skipsBulkUpdateWhenAlreadyRecent() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .lastActivityAt(LocalDateTime.now().minusSeconds(10))
                .build();
        LocalDateTime previousActivityAt = user.getLastActivityAt();

        userSessionService.recordActivity(user);

        verify(userRepository, never()).updateLastActivityAtIfStale(any(), any(), any());
        assertThat(user.getLastActivityAt()).isEqualTo(previousActivityAt);
    }
}
