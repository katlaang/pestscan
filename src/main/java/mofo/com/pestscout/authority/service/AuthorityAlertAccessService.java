package mofo.com.pestscout.authority.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.farm.security.CurrentUserService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorityAlertAccessService {

    private final CurrentUserService currentUserService;

    public User getCurrentUser() {
        return currentUserService.getCurrentUser();
    }

    public void requireCuratorOrSuperAdmin() {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN || Boolean.TRUE.equals(user.getAuthorityAlertCurator())) {
            return;
        }
        throw new ForbiddenException("Only alert curators or super admins can manage authority alerts.");
    }

    public void requireRegionalAnalystOrSuperAdmin() {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.REGIONAL_ANALYST) {
            return;
        }
        throw new ForbiddenException("Only regional analysts or super admins can browse region-wide authority alerts.");
    }
}
