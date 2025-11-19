package mofo.com.pestscout.common;

import mofo.com.pestscout.auth.dto.UpdateUserRequest;
import mofo.com.pestscout.auth.dto.UserDto;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.service.UserService;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import mofo.com.pestscout.farm.model.Farm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private UserService userService;

    private User superAdmin;
    private User farmAdmin;
    private User scout;
    private UUID farmId;

    @BeforeEach
    void setUp() {
        farmId = UUID.randomUUID();

        superAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("superadmin@example.com")
                .firstName("Super")
                .lastName("Admin")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build();

        farmAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("farmadmin@example.com")
                .firstName("Farm")
                .lastName("Admin")
                .role(Role.FARM_ADMIN)
                .isEnabled(true)
                .build();

        scout = User.builder()
                .id(UUID.randomUUID())
                .email("scout@example.com")
                .firstName("Scout")
                .lastName("User")
                .role(Role.SCOUT)
                .isEnabled(true)
                .build();
    }

    @Test
    @DisplayName("SuperAdmin can access any user")
    void getUserById_AsSuperAdmin_ReturnsUser() {
        // Arrange
        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));

        // Act
        UserDto result = userService.getUserById(scout.getId(), superAdmin.getId());

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository).findById(scout.getId());
    }

    @Test
    @DisplayName("Scout can only access their own profile")
    void getUserById_AsScout_AccessingOtherUser_ThrowsUnauthorizedException() {
        // Arrange
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(userRepository.findById(farmAdmin.getId()))
                .thenReturn(Optional.of(farmAdmin));

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(farmAdmin.getId(), scout.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("own profile");
    }

    @Test
    @DisplayName("Scout can access their own profile")
    void getUserById_AsScout_AccessingOwnProfile_ReturnsUser() {
        // Arrange
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));

        // Act
        UserDto result = userService.getUserById(scout.getId(), scout.getId());

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(scout.getEmail());
    }

    @Test
    @DisplayName("Manager can access users in shared farm")
    void getUserById_AsManager_WithSharedFarm_ReturnsUser() {
        // Arrange
        User manager = User.builder()
                .id(UUID.randomUUID())
                .email("manager@example.com")
                .role(Role.MANAGER)
                .isEnabled(true)
                .build();

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

        // Act
        UserDto result = userService.getUserById(scout.getId(), manager.getId());

        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Manager cannot access users without shared farm")
    void getUserById_AsManager_WithoutSharedFarm_ThrowsUnauthorizedException() {
        // Arrange
        User manager = User.builder()
                .id(UUID.randomUUID())
                .email("manager@example.com")
                .role(Role.MANAGER)
                .isEnabled(true)
                .build();

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

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(scout.getId(), manager.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("do not manage");
    }

    @Test
    @DisplayName("Should update user successfully")
    void updateUser_WithValidData_UpdatesUser() {
        // Arrange
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Updated")
                .lastName("Name")
                .email("updated@example.com")
                .build();

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenReturn(scout);

        // Act
        UserDto result = userService.updateUser(scout.getId(), request, superAdmin.getId());

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when email already exists")
    void updateUser_WithExistingEmail_ThrowsConflictException() {
        // Arrange
        UpdateUserRequest request = UpdateUserRequest.builder()
                .email("existing@example.com")
                .build();

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.updateUser(scout.getId(), request, superAdmin.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should encode password when updating")
    void updateUser_WithPassword_EncodesPassword() {
        // Arrange
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

        // Act
        UserDto result = userService.updateUser(scout.getId(), request, superAdmin.getId());

        // Assert
        verify(passwordEncoder).encode(request.getPassword());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should soft delete user")
    void deleteUser_WithValidUser_SoftDeletesUser() {
        // Arrange
        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(userRepository.save(any(User.class)))
                .thenReturn(scout);

        // Act
        userService.deleteUser(scout.getId(), superAdmin.getId());

        // Assert
        verify(userRepository).save(argThat(user ->
                user.getId().equals(scout.getId()) && !user.getIsEnabled()
        ));
    }

    @Test
    @DisplayName("Should get users by farm")
    void getUsersByFarm_AsSuperAdmin_ReturnsUsers() {
        // Arrange
        Farm farm = new Farm();
        farm.setId(farmId);

        UserFarmMembership membership = UserFarmMembership.builder()
                .user(scout)
                .farm(farm)
                .role(Role.SCOUT)
                .isActive(true)
                .build();

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(membershipRepository.findByFarmId(farmId))
                .thenReturn(List.of(membership));

        // Act
        List<UserDto> result = userService.getUsersByFarm(farmId, superAdmin.getId());

        // Assert
        assertThat(result).isNotEmpty();
        verify(membershipRepository).findByFarmId(farmId);
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when non-member tries to list farm users")
    void getUsersByFarm_WithoutMembership_ThrowsUnauthorizedException() {
        // Arrange
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(membershipRepository.existsByUser_IdAndFarmId(scout.getId(), farmId))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.getUsersByFarm(farmId, scout.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    @DisplayName("Should search users in farm")
    void searchUsers_WithQuery_ReturnsPagedResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(scout));

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(membershipRepository.searchActiveUsersInFarm(farmId, "scout", pageable))
                .thenReturn(userPage);

        // Act
        Page<UserDto> result = userService.searchUsers(farmId, "scout", pageable, superAdmin.getId());

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should get user count for farm")
    void getUserCount_WithValidFarm_ReturnsCount() {
        // Arrange
        Farm farm = new Farm();
        farm.setId(farmId);

        UserFarmMembership membership1 = UserFarmMembership.builder()
                .user(scout)
                .farm(farm)
                .isActive(true)
                .build();

        UserFarmMembership membership2 = UserFarmMembership.builder()
                .user(farmAdmin)
                .farm(farm)
                .isActive(true)
                .build();

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(membershipRepository.findByFarmId(farmId))
                .thenReturn(Arrays.asList(membership1, membership2));

        // Act
        long count = userService.getUserCount(farmId, superAdmin.getId());

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Should get active user count for farm")
    void getActiveUserCount_WithMixedUsers_ReturnsActiveCount() {
        // Arrange
        Farm farm = new Farm();
        farm.setId(farmId);

        User disabledUser = User.builder()
                .id(UUID.randomUUID())
                .email("disabled@example.com")
                .role(Role.SCOUT)
                .isEnabled(false)
                .build();

        UserFarmMembership activeMembership = UserFarmMembership.builder()
                .user(scout)
                .farm(farm)
                .isActive(true)
                .build();

        UserFarmMembership inactiveMembership = UserFarmMembership.builder()
                .user(disabledUser)
                .farm(farm)
                .isActive(true)
                .build();

        when(userRepository.findById(superAdmin.getId()))
                .thenReturn(Optional.of(superAdmin));
        when(membershipRepository.findByFarmId(farmId))
                .thenReturn(Arrays.asList(activeMembership, inactiveMembership));

        // Act
        long count = userService.getActiveUserCount(farmId, superAdmin.getId());

        // Assert
        assertThat(count).isEqualTo(1);
    }
}
