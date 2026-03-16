package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.service.LicenseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves analytics visibility for a farm by combining farm membership checks with license policy.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsAccessService {

    private final FarmRepository farmRepository;
    private final UserFarmMembershipRepository membershipRepository;
    private final CurrentUserService currentUserService;
    private final LicenseService licenseService;

    @Transactional(readOnly = true)
    public Farm loadFarmAndEnsureAnalyticsAccess(UUID farmId) {
        Farm farm = loadFarm(farmId);
        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.SUPER_ADMIN) {
            return farm;
        }

        requireManagerVisibility(farm, currentUser);
        licenseService.assertDashboardAccess(farm);
        return farm;
    }

    @Transactional(readOnly = true)
    public Farm loadFarmAndEnsureSuperAdminOrManagerAccess(UUID farmId) {
        Farm farm = loadFarm(farmId);
        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.SUPER_ADMIN) {
            return farm;
        }

        requireManagerVisibility(farm, currentUser);
        return farm;
    }

    private Farm loadFarm(UUID farmId) {
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
    }

    private void requireManagerVisibility(Farm farm, User currentUser) {
        Role role = currentUser.getRole();
        if (role != Role.FARM_ADMIN && role != Role.MANAGER) {
            throw new ForbiddenException("Only farm managers or owners can view analytics for this farm.");
        }

        boolean isOwner = farm.getOwner() != null && farm.getOwner().getId().equals(currentUser.getId());
        boolean isMember = membershipRepository.findByUser_IdAndFarmId(currentUser.getId(), farm.getId())
                .filter(membership -> Boolean.TRUE.equals(membership.getIsActive()))
                .map(membership -> membership.getRole() == Role.FARM_ADMIN || membership.getRole() == Role.MANAGER)
                .orElse(false);

        if (!isOwner && !isMember) {
            throw new ForbiddenException("You do not have analytics access for this farm.");
        }
    }
}
