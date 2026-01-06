package mofo.com.pestscout.farm.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.farm.model.Farm;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FarmAccessService {

    private final CurrentUserService currentUser;

    // ---------------------------------------------------------
    // BASIC ROLE CHECKS
    // ---------------------------------------------------------

    public boolean isSuperAdmin() {
        return currentUser.getCurrentUser().getRole() == Role.SUPER_ADMIN;
    }

    public boolean isManager(User user) {
        return user.getRole() == Role.MANAGER || user.getRole() == Role.FARM_ADMIN;
    }

    public boolean isScout(User user) {
        return user.getRole() == Role.SCOUT;
    }

    // ---------------------------------------------------------
    // RULE #1 — SUPER ADMIN ONLY
    // ---------------------------------------------------------

    public void requireSuperAdmin() {
        User user = currentUser.getCurrentUser();
        if (user.getRole() != Role.SUPER_ADMIN) {
            log.warn("User {} attempted SUPER_ADMIN-only action", user.getEmail());
            throw new ForbiddenException("Only super administrators can perform this action.");
        }
    }

    // ---------------------------------------------------------
    // RULE #2 — VIEW ACCESS
    // SUPER_ADMIN OR owner OR assigned scout
    // ---------------------------------------------------------

    public void requireViewAccess(Farm farm) {
        User user = currentUser.getCurrentUser();

        if (user.getRole() == Role.SUPER_ADMIN) return;

        if (farm.getOwner() != null && farm.getOwner().getId().equals(user.getId())) return;

        log.warn("User {} attempted to VIEW farm {} without permission",
                user.getEmail(), farm.getId());

        throw new ForbiddenException("You do not have permission to view this farm.");
    }

    // ---------------------------------------------------------
    // RULE #3 — ADMIN / MANAGER ACTIONS
    // SUPER_ADMIN OR farm owner
    // ---------------------------------------------------------

    public void requireAdminOrSuperAdmin(Farm farm) {
        User user = currentUser.getCurrentUser();

        if (user.getRole() == Role.SUPER_ADMIN) return;

        if (farm.getOwner() != null && farm.getOwner().getId().equals(user.getId())) {
            return;
        }

        log.warn("User {} attempted ADMIN-ONLY action on farm {}", user.getEmail(), farm.getId());
        throw new ForbiddenException("You are not allowed to modify this farm.");
    }

    // ---------------------------------------------------------
    // RULE #4 — SCOUT-ONLY SESSION OBSERVATION ACTIONS
    // ---------------------------------------------------------

    public void requireScoutOfFarm(Farm farm) {
        User user = currentUser.getCurrentUser();

        if (user.getRole() == Role.SUPER_ADMIN) return; // override

        if (farm.getScout() != null && farm.getScout().getId().equals(user.getId())) {
            return;
        }

        log.warn("User {} attempted SCOUT-ONLY action on farm {}", user.getEmail(), farm.getId());
        throw new ForbiddenException("Only the assigned scout can perform this action.");
    }

    // ---------------------------------------------------------
    // RULE #5 — FARM LISTING
    // Super admin sees all; manager sees farms they own; scout sees assigned farm.
    // This returns a filtering MODE, not a full farm list.
    // ---------------------------------------------------------

    public Role getCurrentUserRole() {
        return currentUser.getCurrentUser().getRole();
    }

    public User getCurrent() {
        return currentUser.getCurrentUser();
    }
}
