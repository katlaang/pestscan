package mofo.com.pestscout.scouting.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.scouting.model.SessionStatus;

import java.util.EnumSet;
import java.util.Set;

public final class SessionStateMachine {

    private static final Set<Role> ADMIN_ROLES = EnumSet.of(Role.SUPER_ADMIN, Role.FARM_ADMIN, Role.MANAGER);

    private SessionStateMachine() {
    }

    public static void assertTransition(SessionStatus from, SessionStatus to, Role role) {
        if (!isTransitionAllowed(from, to, role)) {
            throw new BadRequestException(String.format(
                    "Invalid session transition from %s to %s for role %s",
                    from, to, role));
        }
    }

    private static boolean isTransitionAllowed(SessionStatus from, SessionStatus to, Role role) {
        return switch (to) {
            case NEW -> ADMIN_ROLES.contains(role) && from == SessionStatus.DRAFT;
            case IN_PROGRESS -> EnumSet.of(SessionStatus.NEW, SessionStatus.DRAFT, SessionStatus.REOPENED).contains(from);
            case SUBMITTED -> EnumSet.of(SessionStatus.NEW, SessionStatus.DRAFT, SessionStatus.IN_PROGRESS, SessionStatus.REOPENED).contains(from);
            case COMPLETED -> ADMIN_ROLES.contains(role)
                    && EnumSet.of(SessionStatus.SUBMITTED, SessionStatus.REOPENED).contains(from);
            case REOPENED -> ADMIN_ROLES.contains(role)
                    && EnumSet.of(SessionStatus.SUBMITTED, SessionStatus.COMPLETED, SessionStatus.INCOMPLETE).contains(from);
            case INCOMPLETE -> from == SessionStatus.IN_PROGRESS;
            default -> false;
        };
    }
}
