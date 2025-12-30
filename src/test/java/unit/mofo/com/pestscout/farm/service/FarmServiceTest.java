package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.CreateFarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FarmService Unit Tests")
class FarmServiceTest {

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private FarmAccessService farmAccessService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private FarmService farmService;

    private User superAdmin;
    private User farmOwner;
    private User scout;
    private Farm testFarm;
    private CreateFarmRequest createRequest;
    private UpdateFarmRequest updateRequest;

    @BeforeEach
    void setUp() {
        superAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("superadmin@example.com")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build();

        farmOwner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .role(Role.FARM_ADMIN)
                .isEnabled(true)
                .build();

        scout = User.builder()
                .id(UUID.randomUUID())
                .email("scout@example.com")
                .role(Role.SCOUT)
                .isEnabled(true)
                .build();

        testFarm = Farm.builder()
                .id(UUID.randomUUID())
                .name("Test Farm")
                .description("Test Description")
                .owner(farmOwner)
                .scout(scout)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STANDARD)
                .licensedAreaHectares(new BigDecimal("10.00"))
                .structureType(FarmStructureType.GREENHOUSE)
                .defaultBayCount(5)
                .defaultBenchesPerBay(10)
                .defaultSpotChecksPerBench(3)
                .greenhouses(new ArrayList<>())
                .fieldBlocks(new ArrayList<>())
                .build();

        createRequest = new CreateFarmRequest(
                "New Farm",
                "New Farm Description",
                "EXT-001",
                "123 Farm Road",
                "Farmville",
                "Ontario",
                "N1N 1N1",
                "Canada",
                farmOwner.getId(),
                scout.getId(),
                "John Doe",
                "john@example.com",
                "555-1234",
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.STANDARD,
                "billing@example.com",
                new BigDecimal("15.00"),
                100,
                new BigDecimal("5.00"),
                FarmStructureType.GREENHOUSE,
                5,
                10,
                3,
                new ArrayList<>(),
                new ArrayList<>(),
                "America/Toronto",
                LocalDate.now().plusYears(1),
                true
        );

        updateRequest = new UpdateFarmRequest(
                "Updated Farm Name",
                "Updated Description",
                "EXT-002",
                "456 New Road",
                new BigDecimal("43.123456"),
                new BigDecimal("-80.123456"),
                "New City",
                "Quebec",
                "H1H 1H1",
                "Canada",
                "Jane Smith",
                "jane@example.com",
                "555-5678",
                6,
                12,
                4,
                "America/Montreal"
        );
    }

    @Test
    @DisplayName("SuperAdmin should create farm successfully")
    void createFarm_AsSuperAdmin_CreatesFarm() {
        // Arrange
        when(userRepository.findById(farmOwner.getId()))
                .thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(farmRepository.findByNameIgnoreCase(createRequest.name()))
                .thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class)))
                .thenReturn(testFarm);
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        // Act
        FarmResponse response = farmService.createFarm(createRequest);

        // Assert
        assertThat(response).isNotNull();
        verify(farmAccessService).requireSuperAdmin();
        verify(farmRepository).save(any(Farm.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when farm name exists")
    void createFarm_WithExistingName_ThrowsConflictException() {
        // Arrange
        when(farmRepository.findByNameIgnoreCase(createRequest.name()))
                .thenReturn(Optional.of(testFarm));

        // Act & Assert
        assertThatThrownBy(() -> farmService.createFarm(createRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(farmRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when owner not found")
    void createFarm_WithInvalidOwner_ThrowsResourceNotFoundException() {
        // Arrange
        when(farmRepository.findByNameIgnoreCase(createRequest.name()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(createRequest.ownerId()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> farmService.createFarm(createRequest))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(farmRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update farm successfully")
    void updateFarm_WithValidData_UpdatesFarm() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmRepository.findByNameIgnoreCase(updateRequest.name()))
                .thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class)))
                .thenReturn(testFarm);
        when(farmAccessService.isSuperAdmin())
                .thenReturn(true);
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        // Act
        FarmResponse response = farmService.updateFarm(testFarm.getId(), updateRequest);

        // Assert
        assertThat(response).isNotNull();
        verify(farmAccessService).requireAdminOrSuperAdmin(testFarm);
        verify(farmRepository).save(any(Farm.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when updating to existing name")
    void updateFarm_WithExistingName_ThrowsConflictException() {
        // Arrange
        Farm otherFarm = Farm.builder()
                .id(UUID.randomUUID())
                .name(updateRequest.name())
                .build();

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmRepository.findByNameIgnoreCase(updateRequest.name()))
                .thenReturn(Optional.of(otherFarm));

        // Act & Assert
        assertThatThrownBy(() -> farmService.updateFarm(testFarm.getId(), updateRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(farmRepository, never()).save(any());
    }

    @Test
    @DisplayName("SuperAdmin should see all farms")
    void listFarms_AsSuperAdmin_ReturnsAllFarms() {
        // Arrange
        List<Farm> allFarms = Arrays.asList(testFarm);
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);
        when(farmRepository.findAll())
                .thenReturn(allFarms);

        // Act
        List<FarmResponse> response = farmService.listFarms();

        // Assert
        assertThat(response).isNotEmpty();
        assertThat(response).hasSize(1);
        verify(farmRepository).findAll();
    }

    @Test
    @DisplayName("FarmAdmin should see only owned farms")
    void listFarms_AsFarmAdmin_ReturnsOwnedFarms() {
        // Arrange
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.FARM_ADMIN);
        when(currentUserService.getCurrentUserId())
                .thenReturn(farmOwner.getId());
        when(farmRepository.findByOwnerId(farmOwner.getId()))
                .thenReturn(Arrays.asList(testFarm));

        // Act
        List<FarmResponse> response = farmService.listFarms();

        // Assert
        assertThat(response).isNotEmpty();
        verify(farmRepository).findByOwnerId(farmOwner.getId());
    }

    @Test
    @DisplayName("Scout should see only assigned farm")
    void listFarms_AsScout_ReturnsAssignedFarm() {
        // Arrange
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId())
                .thenReturn(scout.getId());
        when(farmRepository.findByScoutId(scout.getId()))
                .thenReturn(Arrays.asList(testFarm));

        // Act
        List<FarmResponse> response = farmService.listFarms();

        // Assert
        assertThat(response).isNotEmpty();
        verify(farmRepository).findByScoutId(scout.getId());
    }

    @Test
    @DisplayName("Should get farm by ID successfully")
    void getFarm_WithValidId_ReturnsFarm() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        // Act
        FarmResponse response = farmService.getFarm(testFarm.getId());

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo(testFarm.getName());
        verify(farmAccessService).requireViewAccess(testFarm);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for invalid farm ID")
    void getFarm_WithInvalidId_ThrowsResourceNotFoundException() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(farmRepository.findById(invalidId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> farmService.getFarm(invalidId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should hide license data from scouts")
    void getFarm_AsScout_HidesLicenseData() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);

        // Act
        FarmResponse response = farmService.getFarm(testFarm.getId());

        // Assert
        assertThat(response.billingEmail()).isNull();
        assertThat(response.licensedUnitQuota()).isNull();
        assertThat(response.licenseExpiryDate()).isNull();
    }

    @Test
    @DisplayName("Should show license data to managers")
    void getFarm_AsManager_ShowsLicenseData() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);

        // Act
        FarmResponse response = farmService.getFarm(testFarm.getId());

        // Assert
        assertThat(response.licensedAreaHectares()).isNotNull();
    }

    @Test
    @DisplayName("Manager can update non-license fields")
    void updateFarm_AsManager_UpdatesAllowedFields() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmRepository.findByNameIgnoreCase(updateRequest.name()))
                .thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class)))
                .thenReturn(testFarm);
        when(farmAccessService.isSuperAdmin())
                .thenReturn(false);
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);

        // Act
        FarmResponse response = farmService.updateFarm(testFarm.getId(), updateRequest);

        // Assert
        assertThat(response).isNotNull();
        verify(farmRepository).save(argThat(farm ->
                farm.getName().equals(updateRequest.name()) &&
                        farm.getDescription().equals(updateRequest.description())
        ));
    }

    @Test
    @DisplayName("Should apply farm defaults correctly")
    void createFarm_WithDefaults_AppliesCorrectly() {
        // Arrange
        when(userRepository.findById(farmOwner.getId()))
                .thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(farmRepository.findByNameIgnoreCase(createRequest.name()))
                .thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        // Act
        FarmResponse response = farmService.createFarm(createRequest);

        // Assert
        assertThat(response).isNotNull();
        verify(farmRepository).save(argThat(farm ->
                farm.getDefaultBayCount().equals(createRequest.defaultBayCount()) &&
                        farm.getDefaultBenchesPerBay().equals(createRequest.defaultBenchesPerBay()) &&
                        farm.getDefaultSpotChecksPerBench().equals(createRequest.defaultSpotChecksPerBench())
        ));
    }

}