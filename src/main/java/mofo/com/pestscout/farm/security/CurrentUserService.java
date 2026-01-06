package mofo.com.pestscout.farm.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * Returns the currently authenticated User entity.
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            log.warn("Attempted to access secured endpoint without valid authentication");
            throw new UnauthorizedException("You must be logged in to access this resource.");
        }

        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found: " + email));

        if (!user.isActive()) {
            log.warn("Inactive or deleted user {} attempted to access secured endpoint", email);
            throw new UnauthorizedException("Your account is disabled or has been removed.");
        }

        return user;
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }
}

