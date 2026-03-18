package mofo.com.pestscout.farm.security;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.farm.model.Farm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmAccessServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserFarmMembershipRepository membershipRepository;

    @InjectMocks
    private FarmAccessService farmAccessService;

    @Test
    void requireSuperAdmin_throwsForNonSuperAdmin() {
        User manager = User.builder().id(UUID.randomUUID()).email("m@example.com").role(Role.MANAGER).build();
        when(currentUserService.getCurrentUser()).thenReturn(manager);

        assertThatThrownBy(farmAccessService::requireSuperAdmin).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireViewAccess_allowsOwner() {
        User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").role(Role.MANAGER).build();
        Farm farm = new Farm();
        farm.setOwner(owner);

        when(currentUserService.getCurrentUser()).thenReturn(owner);

        farmAccessService.requireViewAccess(farm);
        assertThat(farm.getOwner()).isEqualTo(owner);
    }

    @Test
    void requireAdminOrSuperAdmin_allowsManagerMembership() {
        User manager = User.builder().id(UUID.randomUUID()).email("manager@example.com").role(Role.MANAGER).build();
        Farm farm = new Farm();
        farm.setId(UUID.randomUUID());

        when(currentUserService.getCurrentUser()).thenReturn(manager);
        when(membershipRepository.findByUser_IdAndFarmId(manager.getId(), farm.getId()))
                .thenReturn(java.util.Optional.of(UserFarmMembership.builder()
                        .user(manager)
                        .farm(farm)
                        .role(Role.MANAGER)
                        .isActive(true)
                        .build()));

        farmAccessService.requireAdminOrSuperAdmin(farm);
    }
}
