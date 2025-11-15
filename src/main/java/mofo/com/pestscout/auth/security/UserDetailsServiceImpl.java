package mofo.com.pestscout.auth.security;

import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetailsService implementation for Spring Security.
 * <p>
 * This is the entry point Spring Security uses during authentication to:
 * - look up a user by username (in our case, email)
 * - return a UserDetails object containing password and authorities
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Load a user from the database by email.
     * <p>
     * Spring Security calls this during authentication with the "username"
     * field from the login form. We treat the username as an email address.
     *
     * @param email email address used as username
     * @return Spring Security UserDetails containing credentials and authorities
     * @throws UsernameNotFoundException if no user with the given email exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        LOGGER.debug("Attempting to load user by email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    LOGGER.warn("User not found with email: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        if (!user.isActive()) {
            LOGGER.info("User with email {} is disabled", email);
        } else {
            LOGGER.debug("Successfully loaded active user with email: {}", email);
        }

        // Map our domain User into Spring Security's UserDetails implementation
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isActive(),   // enabled
                true,              // accountNonExpired
                true,              // credentialsNonExpired
                true,              // accountNonLocked
                getAuthorities(user)
        );
    }

    /**
     * Build the collection of GrantedAuthority objects for the user.
     * <p>
     * We expose a single authority with the "ROLE_" prefix required by Spring Security,
     * for example: ROLE_SCOUT, ROLE_MANAGER, ROLE_FARM_ADMIN, ROLE_SUPER_ADMIN.
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }
}

