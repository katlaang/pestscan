package mofo.com.pestscout.auth.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserRepository userRepository;

    @Value("${app.auth.idle-timeout-minutes:5}")
    private long idleTimeoutMinutes;

    @Value("${app.auth.activity-write-throttle-seconds:30}")
    private long activityWriteThrottleSeconds;

    public boolean isIdleExpired(User user) {
        LocalDateTime lastSeenAt = resolveLastSeenAt(user);
        if (lastSeenAt == null) {
            return false;
        }
        return lastSeenAt.isBefore(LocalDateTime.now().minusMinutes(idleTimeoutMinutes));
    }

    @Transactional
    public void recordActivity(User user) {
        if (user.getId() == null) {
            return;
        }

        LocalDateTime activityAt = LocalDateTime.now();
        LocalDateTime minimumPreviousActivityAt = activityAt.minusSeconds(activityWriteThrottleSeconds);

        if (user.getLastActivityAt() != null && !user.getLastActivityAt().isBefore(minimumPreviousActivityAt)) {
            return;
        }

        userRepository.updateLastActivityAtIfStale(user.getId(), activityAt, minimumPreviousActivityAt);
        user.setLastActivityAt(activityAt);
    }

    private LocalDateTime resolveLastSeenAt(User user) {
        if (user.getLastActivityAt() != null) {
            return user.getLastActivityAt();
        }
        if (user.getLastLogin() != null) {
            return user.getLastLogin();
        }
        return user.getCreatedAt();
    }
}
