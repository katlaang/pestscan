package mofo.com.pestscout.auth.service;

import mofo.com.pestscout.auth.dto.UpdateUserRequest;
import mofo.com.pestscout.auth.dto.UserDto;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.model.Farm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFarmMembershipRepository membershipRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private UserService userService;

    private UUID farmId;
    private User superAdmin;
    private User farmAdmin;
    private User scout;

    @BeforeEach
    void setUp() {
        farmId = UUID.randomUUID();
        superAdmin = buildUser(UUID.randomUUID(), Role.SUPER_ADMIN);
        farmAdmin = buildUser(UUID.randomUUID(), Role.FARM_ADMIN);
        scout = buildUser(UUID.randomUUID(), Role.SCOUT);
    }

    // Helpers

    private User buildUser(UUID id, Role role) {
        return User.builder()
                .id(id)
                .email(role.name().toLowerCase() + "@example.com")
                .firstName(role.name())
                .lastName("User")
                .role(role)
                .isEnabled(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private UserFarmMembership buildMembership(User user, UUID farmId, boolean active, Role role) {
        Farm farm = Farm.builder()
                .id(farmId)
                .name("Farm-" + farmId)
                .build();

        return UserFarmMembership.builder()
                .user(user)
                .farm(farm)
                .isActive(active)
                .role(role)
                .build();
    }

    // -------- getUserById tests --------

    @Test
    @DisplayName("SUPER_ADMIN can view any user via getUserById")
    void getUserById_superAdminCanAccessAnyUser() {
        UUID targetId = UUID.randomUUID();
        User target = buildUser(targetId, Role.SCOUT);

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        UserDto dto = userService.getUserById(targetId, superAdmin.getId());

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(targetId);
        verify(userRepository, times(2)).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Scout cannot view other users")
    void getUserById_scoutCannotAccessOtherUser() {
        UUID targetId = UUID.randomUUID();
        User target = buildUser(targetId, Role.MANAGER);

        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.getUserById(targetId, scout.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Scouts can only access their own profile");
    }

    @Test
    @DisplayName("Scout can access their own profile")
    void getUserById_AsScout_AccessingOwnProfile_ReturnsUser() {
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));

        UserDto result = userService.getUserById(scout.getId(), scout.getId());

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(scout.getEmail());
    }

    @Test
    @DisplayName("Manager can access users in shared farm")
    void getUserById_AsManager_WithSharedFarm_ReturnsUser() {
        User manager = buildUser(UUID.randomUUID(), Role.MANAGER);

        Farm farm = new Farm();
        farm.setId(farmId);

        UserFarmMembership managerMembership = UserFarmMembership.builder()
                .user(manager)
                .farm(farm)
                .role(Role.MANAGER)
                .isActive(true)
                .build();

        UserFarmMembership scoutMembership = UserFarmMembership.builder()
                .user(scout)
                .farm(farm)
                .role(Role.SCOUT)
                .isActive(true)
                .build();

        when(userRepository.findById(manager.getId()))
                .thenReturn(Optional.of(manager));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(membershipRepository.findByUser_Id(manager.getId()))
                .thenReturn(List.of(managerMembership));
        when(membershipRepository.findByUser_Id(scout.getId()))
                .thenReturn(List.of(scoutMembership));

        UserDto result = userService.getUserById(scout.getId(), manager.getId());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(scout.getId());
    }

    @Test
    @DisplayName("Manager cannot access users without shared farm")
    void getUserById_AsManager_WithoutSharedFarm_ThrowsUnauthorizedException() {
        User manager = buildUser(UUID.randomUUID(), Role.MANAGER);

        Farm farm1 = new Farm();
        farm1.setId(UUID.randomUUID());

        Farm farm2 = new Farm();
        farm2.setId(UUID.randomUUID());

        UserFarmMembership managerMembership = UserFarmMembership.builder()
                .user(manager)
                .farm(farm1)
                .role(Role.MANAGER)
                .isActive(true)
                .build();

        UserFarmMembership scoutMembership = UserFarmMembership.builder()
                .user(scout)
                .farm(farm2)
                .role(Role.SCOUT)
                .isActive(true)
                .build();

        when(userRepository.findById(manager.getId()))
                .thenReturn(Optional.of(manager));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(membershipRepository.findByUser_Id(manager.getId()))
                .thenReturn(List.of(managerMembership));
        when(membershipRepository.findByUser_Id(scout.getId()))
                .thenReturn(List.of(scoutMembership));

        assertThatThrownBy(() -> userService.getUserById(scout.getId(), manager.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("do not manage");
    }

    @Test
    @DisplayName("getUserById throws ResourceNotFoundException when target user is missing")
    void getUserById_missingUserThrows() {
        UUID targetId = UUID.randomUUID();

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(targetId, superAdmin.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------- getUsersByFarm tests --------

    @Test
    @DisplayName("SUPER_ADMIN can list all users for a farm")
    void getUsersByFarm_superAdminListsAllFarmUsers() {
        UUID farmId = UUID.randomUUID();

        User u1 = buildUser(UUID.randomUUID(), Role.MANAGER);
        User u2 = buildUser(UUID.randomUUID(), Role.SCOUT);

        UserFarmMembership m1 = buildMembership(u1, farmId, true, Role.MANAGER);
        UserFarmMembership m2 = buildMembership(u2, farmId, true, Role.SCOUT);

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(membershipRepository.findByFarmId(farmId))
                .thenReturn(List.of(m1, m2));

        List<UserDto> users = userService.getUsersByFarm(farmId, superAdmin.getId());

        assertThat(users).hasSize(2);
        assertThat(users)
                .extracting(UserDto::getId)
                .containsExactlyInAnyOrder(u1.getId(), u2.getId());

        verify(membershipRepository, never()).existsByUser_IdAndFarmId(any(), any());
    }

    @Test
    @DisplayName("MANAGER without membership in farm cannot list users")
    void getUsersByFarm_managerWithoutMembershipThrows() {
        UUID farmId = UUID.randomUUID();
        User manager = buildUser(UUID.randomUUID(), Role.MANAGER);

        when(userRepository.findById(manager.getId()))
                .thenReturn(Optional.of(manager));
        when(membershipRepository.existsByUser_IdAndFarmId(manager.getId(), farmId))
                .thenReturn(false);

        assertThatThrownBy(() -> userService.getUsersByFarm(farmId, manager.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("do not have access to this farm");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when non-member tries to list farm users")
    void getUsersByFarm_WithoutMembership_ThrowsUnauthorizedException() {
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(membershipRepository.existsByUser_IdAndFarmId(scout.getId(), farmId))
                .thenReturn(false);

        assertThatThrownBy(() -> userService.getUsersByFarm(farmId, scout.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("do not have access");
    }

    // -------- updateUser tests --------

    @Test
    @DisplayName("updateUser updates fields and evicts cache when requester is authorized")
    void updateUser_updatesFieldsAndEvictsCache() {
        UUID targetId = UUID.randomUUID();
        User target = buildUser(targetId, Role.MANAGER);

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));
        when(userRepository.existsByEmail("new@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("secret"))
                .thenReturn("encoded-secret");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserRequest request = UpdateUserRequest.builder()
                .email("new@example.com")
                .password("secret")
                .firstName("NewFirst")
                .lastName("NewLast")
                .role(Role.FARM_ADMIN)
                .isEnabled(false)
                .build();

        UserDto result = userService.updateUser(targetId, request, superAdmin.getId());

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getFirstName()).isEqualTo("NewFirst");
        assertThat(result.getLastName()).isEqualTo("NewLast");
        assertThat(result.getRole()).isEqualTo(Role.FARM_ADMIN);
        assertThat(result.getIsEnabled()).isFalse();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-secret");

        verify(cacheService).evictUserCache(targetId);
    }

    @Test
    @DisplayName("updateUser throws ConflictException when email already exists")
    void updateUser_emailAlreadyInUseThrows() {
        UUID targetId = UUID.randomUUID();
        User target = buildUser(targetId, Role.MANAGER);

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));
        when(userRepository.existsByEmail("new@example.com"))
                .thenReturn(true);

        UpdateUserRequest request = UpdateUserRequest.builder()
                .email("new@example.com")
                .build();

        assertThatThrownBy(() -> userService.updateUser(targetId, request, superAdmin.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should encode password when updating")
    void updateUser_WithPassword_EncodesPassword() {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .password("newPassword123")
                .build();

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(passwordEncoder.encode(request.getPassword()))
                .thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
                .thenReturn(scout);

        userService.updateUser(scout.getId(), request, superAdmin.getId());

        verify(passwordEncoder).encode(request.getPassword());
        verify(userRepository).save(any(User.class));
    }

    // -------- deleteUser tests --------

    @Test
    @DisplayName("deleteUser disables user and evicts cache")
    void deleteUser_softDeletesUser() {
        UUID targetId = UUID.randomUUID();
        User target = buildUser(targetId, Role.SCOUT);

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.deleteUser(targetId, superAdmin.getId());

        assertThat(target.getIsEnabled()).isFalse();
        verify(userRepository).save(target);
        verify(cacheService).evictUserCache(targetId);
    }

    // -------- searchUsers tests --------

    @Test
    @DisplayName("searchUsers delegates to membership repository and maps to DTOs")
    void searchUsers_usesMembershipSearch() {
        PageRequest pageable = PageRequest.of(0, 10);
        User u1 = buildUser(UUID.randomUUID(), Role.MANAGER);
        Page<User> page = new PageImpl<>(List.of(u1));

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(membershipRepository.searchActiveUsersInFarm(eq(farmId), eq("john"), any()))
                .thenReturn(page);

        Page<UserDto> result = userService.searchUsers(farmId, "john", pageable, superAdmin.getId());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(u1.getId());
    }

    // -------- stats tests --------

    @Test
    @DisplayName("getUserCount counts distinct active users")
    void getUserCount_countsDistinctActiveUsers() {
        User u1 = buildUser(UUID.randomUUID(), Role.MANAGER);
        User u2 = buildUser(UUID.randomUUID(), Role.SCOUT);

        UserFarmMembership m1 = buildMembership(u1, farmId, true, Role.MANAGER);
        UserFarmMembership m2 = buildMembership(u1, farmId, true, Role.MANAGER); // duplicate user
        UserFarmMembership m3 = buildMembership(u2, farmId, false, Role.SCOUT);  // inactive

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(membershipRepository.findByFarmId(farmId))
                .thenReturn(List.of(m1, m2, m3));

        long count = userService.getUserCount(farmId, superAdmin.getId());

        // only u1 active and distinct
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("getActiveUserCount counts only active memberships and enabled users")
    void getActiveUserCount_filtersByEnabledFlag() {
        User activeUser = buildUser(UUID.randomUUID(), Role.MANAGER);
        activeUser.setIsEnabled(true);
        User disabledUser = buildUser(UUID.randomUUID(), Role.SCOUT);
        disabledUser.setIsEnabled(false);

        UserFarmMembership m1 = buildMembership(activeUser, farmId, true, Role.MANAGER);
        UserFarmMembership m2 = buildMembership(disabledUser, farmId, true, Role.SCOUT);

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(membershipRepository.findByFarmId(farmId))
                .thenReturn(List.of(m1, m2));

        long count = userService.getActiveUserCount(farmId, superAdmin.getId());

        assertThat(count).isEqualTo(1L);
    }
}
