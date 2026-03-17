package mofo.com.pestscout.auth.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminStartupMonitor Unit Tests")
class SuperAdminStartupMonitorTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SuperAdminStartupMonitor monitor;

    @Test
    @DisplayName("Should check for a super admin at startup")
    void warnWhenMissingSuperAdmin_ChecksRepository() {
        when(userRepository.existsByRoleAndDeletedFalse(Role.SUPER_ADMIN)).thenReturn(false);

        monitor.warnWhenMissingSuperAdmin();

        verify(userRepository).existsByRoleAndDeletedFalse(Role.SUPER_ADMIN);
    }
}
