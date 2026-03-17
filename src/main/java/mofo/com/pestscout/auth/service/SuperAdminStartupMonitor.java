package mofo.com.pestscout.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Emits a startup warning when the platform has no super admin yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminStartupMonitor {

    private final UserRepository userRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void warnWhenMissingSuperAdmin() {
        if (!userRepository.existsByRoleAndDeletedFalse(Role.SUPER_ADMIN)) {
            log.warn("No SUPER_ADMIN exists. Create the initial super admin via POST /api/auth/bootstrap/super-admin.");
        }
    }
}
