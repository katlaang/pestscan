package mofo.com.pestscout.unit.farm.security;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
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
}
