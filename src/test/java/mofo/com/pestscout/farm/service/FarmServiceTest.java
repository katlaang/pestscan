package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.service.CustomerNumberService;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.*;
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
    private UserFarmMembershipRepository membershipRepository;

    @Mock
    private CustomerNumberService customerNumberService;

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
                .address("123 Existing Road")
                .city("Old City")
                .province("Old Province")
                .postalCode("A1A1A1")
                .country("Canada")
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                6,
                12,
                4,
                "America/Montreal",
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("SuperAdmin should create farm successfully")
    void createFarm_AsSuperAdmin_CreatesFarm() {
        // Arrange
        when(customerNumberService.normalizeCountryCode(createRequest.country()))
                .thenReturn("CA");
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
    @DisplayName("SuperAdmin can create farm without owner or scout")
    void createFarm_WithoutAssignedUsers_AllowsUnassignedFarm() {
        CreateFarmRequest unassignedRequest = new CreateFarmRequest(
                "Unassigned Farm",
                "No owner yet",
                "123 Farm Road",
                "Farmville",
                "Ontario",
                "N1N 1N1",
                "Canada",
                new UUID(0L, 0L),
                new UUID(0L, 0L),
                "Ops Contact",
                "ops@example.com",
                "555-1234",
                SubscriptionStatus.PENDING_ACTIVATION,
                SubscriptionTier.BASIC,
                "billing@example.com",
                new BigDecimal("5.00"),
                10,
                BigDecimal.ZERO,
                FarmStructureType.GREENHOUSE,
                2,
                4,
                2,
                new ArrayList<>(),
                new ArrayList<>(),
                "America/Toronto",
                LocalDate.now().plusYears(1),
                false
        );

        when(customerNumberService.normalizeCountryCode(unassignedRequest.country()))
                .thenReturn("CA");
        when(farmRepository.findByNameIgnoreCase(unassignedRequest.name()))
                .thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.createFarm(unassignedRequest);

        assertThat(response.ownerId()).isNull();
        assertThat(response.scoutId()).isNull();
        assertThat(response.contactName()).isEqualTo("Ops Contact");
        verify(userRepository, never()).findById(new UUID(0L, 0L));
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
        when(customerNumberService.normalizeCountryCode(createRequest.country()))
                .thenReturn("CA");
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
    @DisplayName("SuperAdmin can assign owner and scout later")
    void updateFarm_WithOwnerAndScoutAssignment_UpdatesAssignments() {
        UpdateFarmRequest assignmentRequest = new UpdateFarmRequest(
                "Updated Farm Name",
                "Updated Description",
                "456 New Road",
                new BigDecimal("43.123456"),
                new BigDecimal("-80.123456"),
                "New City",
                "Quebec",
                "H1H 1H1",
                "Canada",
                null,
                "jane@example.com",
                "555-5678",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                6,
                12,
                4,
                "America/Montreal",
                farmOwner.getId(),
                scout.getId(),
                null
        );

        testFarm.setOwner(null);
        testFarm.setScout(null);

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmRepository.findByNameIgnoreCase(assignmentRequest.name()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(farmOwner.getId()))
                .thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(farmRepository.save(any(Farm.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.isSuperAdmin())
                .thenReturn(true);
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.updateFarm(testFarm.getId(), assignmentRequest);

        assertThat(response.ownerId()).isEqualTo(farmOwner.getId());
        assertThat(response.scoutId()).isEqualTo(scout.getId());
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
    @DisplayName("Manager should see farms through active memberships")
    void listFarms_AsManager_ReturnsMembershipFarms() {
        User manager = User.builder()
                .id(UUID.randomUUID())
                .email("manager@example.com")
                .role(Role.MANAGER)
                .isEnabled(true)
                .build();
        Farm membershipFarm = Farm.builder()
                .id(UUID.randomUUID())
                .name("Membership Farm")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STANDARD)
                .structureType(FarmStructureType.GREENHOUSE)
                .licensedAreaHectares(new BigDecimal("3.00"))
                .build();

        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.MANAGER);
        when(currentUserService.getCurrentUserId()).thenReturn(manager.getId());
        when(farmRepository.findByOwnerId(manager.getId())).thenReturn(List.of());
        when(membershipRepository.findByUser_Id(manager.getId())).thenReturn(List.of(
                UserFarmMembership.builder()
                        .user(manager)
                        .farm(membershipFarm)
                        .role(Role.MANAGER)
                        .isActive(true)
                        .build()
        ));

        List<FarmResponse> response = farmService.listFarms();

        assertThat(response).singleElement().satisfies(farm -> assertThat(farm.id()).isEqualTo(membershipFarm.getId()));
    }

    @Test
    @DisplayName("Scout should not list farms")
    void listFarms_AsScout_ThrowsForbiddenException() {
        // Arrange
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);

        // Act & Assert
        assertThatThrownBy(() -> farmService.listFarms())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Scouts cannot access farm dashboards");
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
    @DisplayName("SuperAdmin can partially update farm access without sending name")
    void updateFarm_WithAccessOnlyPayload_PreservesExistingMetadata() {
        UpdateFarmRequest accessRequest = new UpdateFarmRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                SubscriptionStatus.SUSPENDED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Boolean.TRUE
        );

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmRepository.save(any(Farm.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.isSuperAdmin())
                .thenReturn(true);
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.updateFarm(testFarm.getId(), accessRequest);

        assertThat(response.name()).isEqualTo("Test Farm");
        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(response.accessLocked()).isTrue();
        verify(farmRepository).save(argThat(farm ->
                farm.getName().equals("Test Farm")
                        && farm.getDescription().equals("Test Description")
                        && Boolean.TRUE.equals(farm.getIsArchived())
        ));
    }

    @Test
    @DisplayName("Should apply farm defaults correctly")
    void createFarm_WithDefaults_AppliesCorrectly() {
        // Arrange
        when(customerNumberService.normalizeCountryCode(createRequest.country()))
                .thenReturn("CA");
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

    @Test
    @DisplayName("Should infer FIELD structure type from field blocks when type omitted")
    void createFarm_WithFieldBlocksAndNoStructureType_InfersField() {
        CreateFarmRequest fieldFarmRequest = new CreateFarmRequest(
                "Field Farm",
                "Open field setup",
                "123 Field Road",
                "Farmville",
                "Ontario",
                "N1N 1N1",
                "Canada",
                null,
                null,
                "Field Ops",
                "field@example.com",
                "555-0000",
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                "billing@example.com",
                new BigDecimal("20.00"),
                50,
                BigDecimal.ZERO,
                null,
                9,
                0,
                4,
                new ArrayList<>(),
                List.of(new CreateFieldBlockRequest("North Field", null, null, List.of("Row-1"), true)),
                "America/Toronto",
                LocalDate.now().plusYears(1),
                false
        );

        when(customerNumberService.normalizeCountryCode(fieldFarmRequest.country())).thenReturn("CA");
        when(farmRepository.findByNameIgnoreCase(fieldFarmRequest.name())).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.createFarm(fieldFarmRequest);

        assertThat(response.structureType()).isEqualTo(FarmStructureType.FIELD);
        verify(farmRepository).save(argThat(farm ->
                farm.getStructureType() == FarmStructureType.FIELD
                        && farm.getFieldBlocks().size() == 1
                        && farm.getFieldBlocks().getFirst().getBayCount() == 9
                        && farm.getFieldBlocks().getFirst().getSpotChecksPerBay() == 4
        ));
    }

    @Test
    @DisplayName("Should generate bay and bench tags when farm structures omit them")
    void createFarm_WithoutStructureTags_GeneratesDefaults() {
        CreateFarmRequest request = new CreateFarmRequest(
                "Tagged Farm",
                "Generated layout tags",
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
                4,
                3,
                2,
                List.of(new CreateGreenhouseRequest("House 1", null, 2, 3, 2, List.of(), List.of(), new BigDecimal("1.50"))),
                List.of(new CreateFieldBlockRequest("Field 1", 2, 2, List.of(), true, new BigDecimal("2.00"))),
                "America/Toronto",
                LocalDate.now().plusYears(1),
                true
        );

        when(customerNumberService.normalizeCountryCode(request.country())).thenReturn("CA");
        when(userRepository.findById(farmOwner.getId())).thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId())).thenReturn(Optional.of(scout));
        when(farmRepository.findByNameIgnoreCase(request.name())).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.createFarm(request);

        assertThat(response.name()).isEqualTo("Tagged Farm");
        verify(farmRepository).save(argThat(farm ->
                farm.getGreenhouses().getFirst().getBayTags().equals(List.of("Bay-1", "Bay-2"))
                        && farm.getGreenhouses().getFirst().getBenchTags().equals(List.of("Bed-1", "Bed-2", "Bed-3"))
                        && farm.getFieldBlocks().getFirst().getBayTags().equals(List.of("Bay-1", "Bay-2"))
        ));
    }

}
