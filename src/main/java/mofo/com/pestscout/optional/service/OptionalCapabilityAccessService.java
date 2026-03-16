package mofo.com.pestscout.optional.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OptionalCapabilityAccessService {

    private final FarmRepository farmRepository;
    private final UserFarmMembershipRepository membershipRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public Farm loadFarmAndEnsureViewer(UUID farmId) {
        Farm farm = loadFarm(farmId);
        User currentUser = currentUserService.getCurrentUser();
        Role role = currentUser.getRole();

        if (role == Role.SUPER_ADMIN) {
            return farm;
        }

        boolean isOwner = farm.getOwner() != null && farm.getOwner().getId().equals(currentUser.getId());
        boolean isMember = membershipRepository.existsByUser_IdAndFarmId(currentUser.getId(), farmId);
        boolean isAssignedScout = farm.getScout() != null && farm.getScout().getId().equals(currentUser.getId());

        if ((role == Role.FARM_ADMIN || role == Role.MANAGER) && (isOwner || isMember)) {
            return farm;
        }

        if (role == Role.SCOUT && isAssignedScout) {
            return farm;
        }

        throw new ForbiddenException("You do not have permission to access optional capabilities for this farm.");
    }

    @Transactional(readOnly = true)
    public Farm loadFarmAndEnsureManager(UUID farmId) {
        Farm farm = loadFarm(farmId);
        User currentUser = currentUserService.getCurrentUser();
        Role role = currentUser.getRole();

        if (role == Role.SUPER_ADMIN) {
            return farm;
        }

        boolean isOwner = farm.getOwner() != null && farm.getOwner().getId().equals(currentUser.getId());
        boolean isMember = membershipRepository.existsByUser_IdAndFarmId(currentUser.getId(), farmId);

        if ((role == Role.FARM_ADMIN || role == Role.MANAGER) && (isOwner || isMember)) {
            return farm;
        }

        throw new ForbiddenException("You do not have manager access to optional capabilities for this farm.");
    }

    private Farm loadFarm(UUID farmId) {
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
    }
}
