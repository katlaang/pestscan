package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.service.CustomerNumberService;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.feature.FarmFeatureEntitlementRepository;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.*;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;
import mofo.com.pestscout.farm.repository.FarmLicenseHistoryRepository;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.optional.repository.SupplyOrderRequestRepository;
import mofo.com.pestscout.region.service.NorthAmericaRegionService;
import mofo.com.pestscout.scouting.repository.CustomSpeciesDefinitionRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
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

    @Mock
    private FarmAreaAllocationService farmAreaAllocationService;

    @Mock
    private FarmLicenseHistoryRepository farmLicenseHistoryRepository;

    @Mock
    private ScoutingSessionRepository scoutingSessionRepository;

    @Mock
    private FarmFeatureEntitlementRepository farmFeatureEntitlementRepository;

    @Mock
    private CustomSpeciesDefinitionRepository customSpeciesDefinitionRepository;

    @Mock
    private SupplyOrderRequestRepository supplyOrderRequestRepository;

    @Mock
    private NorthAmericaRegionService northAmericaRegionService;

    @InjectMocks
    private FarmService farmService;

    private User superAdmin;
    private User farmOwner;
    private User scout;
    private User managerMember;
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

        managerMember = User.builder()
                .id(UUID.randomUUID())
                .email("manager@example.com")
                .role(Role.MANAGER)
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

        createRequest = CreateFarmRequest.builder()
                .name("New Farm")
                .description("New Farm Description")
                .address("123 Farm Road")
                .city("Farmville")
                .province("Ontario")
                .postalCode("N1N 1N1")
                .country("Canada")
                .organic(false)
                .ownerId(farmOwner.getId())
                .scoutId(scout.getId())
                .contactName("John Doe")
                .contactEmail("john@example.com")
                .contactPhone("555-1234")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STANDARD)
                .billingEmail("billing@example.com")
                .licensedAreaHectares(new BigDecimal("15.00"))
                .licensedUnitQuota(100)
                .quotaDiscountPercentage(new BigDecimal("5.00"))
                .structureType(FarmStructureType.GREENHOUSE)
                .defaultBayCount(5)
                .defaultBenchesPerBay(10)
                .defaultSpotChecksPerBench(3)
                .greenhouses(new ArrayList<>())
                .fieldBlocks(new ArrayList<>())
                .timezone("America/Toronto")
                .licenseExpiryDate(LocalDate.now().plusYears(1))
                .autoRenewEnabled(true)
                .build();

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

        lenient().when(northAmericaRegionService.normalizeCountry(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(northAmericaRegionService.normalizeState(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    @DisplayName("SuperAdmin should create farm successfully")
    void createFarm_AsSuperAdmin_CreatesFarm() {
        // Arrange
        when(customerNumberService.normalizeCountryCode(createRequest.getCountry()))
                .thenReturn("CA");
        when(userRepository.findById(farmOwner.getId()))
                .thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(farmRepository.findByNameIgnoreCase(createRequest.getName()))
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
        verify(farmAreaAllocationService).validateFarmStructureAreas(
                createRequest.getLicensedAreaHectares(),
                createRequest.getGreenhouses(),
                createRequest.getFieldBlocks()
        );
        verify(farmRepository).save(any(Farm.class));
    }

    @Test
    @DisplayName("Should generate a unique URL slug when creating a farm")
    void createFarm_GeneratesUniqueSlug() {
        when(customerNumberService.normalizeCountryCode(createRequest.getCountry()))
                .thenReturn("CA");
        when(userRepository.findById(farmOwner.getId()))
                .thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(farmRepository.findByNameIgnoreCase(createRequest.getName()))
                .thenReturn(Optional.empty());
        when(farmRepository.existsBySlug("new-farm"))
                .thenReturn(true);
        when(farmRepository.existsBySlug("new-farm-2"))
                .thenReturn(false);
        when(farmRepository.save(any(Farm.class)))
                .thenAnswer(invocation -> {
                    Farm farm = invocation.getArgument(0);
                    farm.setId(UUID.randomUUID());
                    return farm;
                });
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.createFarm(createRequest);

        assertThat(response.slug()).isEqualTo("new-farm-2");
        verify(farmRepository).save(argThat(farm -> "new-farm-2".equals(farm.getSlug())));
    }

    @Test
    @DisplayName("SuperAdmin can create farm without owner or scout")
    void createFarm_WithoutAssignedUsers_AllowsUnassignedFarm() {
        CreateFarmRequest unassignedRequest = CreateFarmRequest.builder()
                .name("Unassigned Farm")
                .description("No owner yet")
                .address("123 Farm Road")
                .city("Farmville")
                .province("Ontario")
                .postalCode("N1N 1N1")
                .country("Canada")
                .ownerId(new UUID(0L, 0L))
                .scoutId(new UUID(0L, 0L))
                .contactName("Ops Contact")
                .contactEmail("ops@example.com")
                .contactPhone("555-1234")
                .subscriptionStatus(SubscriptionStatus.PENDING_ACTIVATION)
                .subscriptionTier(SubscriptionTier.BASIC)
                .billingEmail("billing@example.com")
                .licensedAreaHectares(new BigDecimal("5.00"))
                .licensedUnitQuota(10)
                .quotaDiscountPercentage(BigDecimal.ZERO)
                .structureType(FarmStructureType.GREENHOUSE)
                .defaultBayCount(2)
                .defaultBenchesPerBay(4)
                .defaultSpotChecksPerBench(2)
                .greenhouses(new ArrayList<>())
                .fieldBlocks(new ArrayList<>())
                .timezone("America/Toronto")
                .licenseExpiryDate(LocalDate.now().plusYears(1))
                .autoRenewEnabled(false)
                .build();

        when(customerNumberService.normalizeCountryCode(unassignedRequest.getCountry()))
                .thenReturn("CA");
        when(farmRepository.findByNameIgnoreCase(unassignedRequest.getName()))
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
    @DisplayName("Should persist create-time coordinates and attach requested members")
    void createFarm_WithCoordinatesAndMembers_PersistsCoordinatesAndMemberships() {
        CreateFarmRequest request = CreateFarmRequest.builder()
                .name("Coordinate Farm")
                .description("Mapped farm")
                .address("123 Farm Road")
                .city("Farmville")
                .province("Ontario")
                .postalCode("N1N 1N1")
                .country("Canada")
                .organic(true)
                .ownerId(farmOwner.getId())
                .scoutId(scout.getId())
                .contactName("John Doe")
                .contactEmail("john@example.com")
                .contactPhone("555-1234")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STANDARD)
                .billingEmail("billing@example.com")
                .licensedAreaHectares(new BigDecimal("15.00"))
                .licensedUnitQuota(100)
                .quotaDiscountPercentage(new BigDecimal("5.00"))
                .structureType(FarmStructureType.GREENHOUSE)
                .defaultBayCount(5)
                .defaultBenchesPerBay(10)
                .defaultSpotChecksPerBench(3)
                .greenhouses(new ArrayList<>())
                .fieldBlocks(new ArrayList<>())
                .timezone("America/Toronto")
                .licenseExpiryDate(LocalDate.now().plusYears(1))
                .autoRenewEnabled(true)
                .latitude(new BigDecimal("43.1234567"))
                .longitude(new BigDecimal("-80.1234567"))
                .memberAssignments(List.of(new FarmMemberAssignmentRequest(managerMember.getId(), Role.MANAGER)))
                .build();

        when(customerNumberService.normalizeCountryCode(request.getCountry())).thenReturn("CA");
        when(userRepository.findById(farmOwner.getId())).thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId())).thenReturn(Optional.of(scout));
        when(userRepository.findById(managerMember.getId())).thenReturn(Optional.of(managerMember));
        when(farmRepository.findByNameIgnoreCase(request.getName())).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> {
            Farm farm = invocation.getArgument(0);
            farm.setId(UUID.randomUUID());
            return farm;
        });
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);
        when(membershipRepository.findByFarmId(any())).thenReturn(List.of());

        FarmResponse response = farmService.createFarm(request);

        assertThat(response.latitude()).isEqualByComparingTo("43.1234567");
        assertThat(response.longitude()).isEqualByComparingTo("-80.1234567");
        assertThat(response.organic()).isTrue();
        assertThat(response.organicLabel()).isEqualTo("Organic");
        verify(farmRepository).save(argThat(farm -> Boolean.TRUE.equals(farm.getOrganic())));
        verify(membershipRepository, times(3)).save(any(UserFarmMembership.class));
    }

    @Test
    @DisplayName("Should allow assigning a manager who is already attached to another farm")
    void createFarm_WithManagerAttachedToAnotherFarm_AllowsMultiFarmMembership() {
        Farm otherFarm = Farm.builder()
                .id(UUID.randomUUID())
                .name("Other Farm")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STANDARD)
                .licensedAreaHectares(new BigDecimal("5.00"))
                .structureType(FarmStructureType.GREENHOUSE)
                .build();

        UserFarmMembership existingMembership = UserFarmMembership.builder()
                .user(managerMember)
                .farm(otherFarm)
                .role(Role.MANAGER)
                .isActive(true)
                .build();

        CreateFarmRequest request = CreateFarmRequest.builder()
                .name("Conflicting Farm")
                .description("Farm with duplicate member")
                .address("123 Farm Road")
                .city("Farmville")
                .province("Ontario")
                .postalCode("N1N 1N1")
                .country("Canada")
                .ownerId(farmOwner.getId())
                .scoutId(scout.getId())
                .contactName("John Doe")
                .contactEmail("john@example.com")
                .contactPhone("555-1234")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STANDARD)
                .billingEmail("billing@example.com")
                .licensedAreaHectares(new BigDecimal("15.00"))
                .licensedUnitQuota(100)
                .quotaDiscountPercentage(new BigDecimal("5.00"))
                .structureType(FarmStructureType.GREENHOUSE)
                .defaultBayCount(5)
                .defaultBenchesPerBay(10)
                .defaultSpotChecksPerBench(3)
                .greenhouses(new ArrayList<>())
                .fieldBlocks(new ArrayList<>())
                .timezone("America/Toronto")
                .licenseExpiryDate(LocalDate.now().plusYears(1))
                .autoRenewEnabled(true)
                .memberAssignments(List.of(new FarmMemberAssignmentRequest(managerMember.getId(), Role.MANAGER)))
                .build();

        when(customerNumberService.normalizeCountryCode(request.getCountry())).thenReturn("CA");
        when(userRepository.findById(farmOwner.getId())).thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId())).thenReturn(Optional.of(scout));
        when(userRepository.findById(managerMember.getId())).thenReturn(Optional.of(managerMember));
        when(farmRepository.findByNameIgnoreCase(request.getName())).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> {
            Farm farm = invocation.getArgument(0);
            farm.setId(UUID.randomUUID());
            return farm;
        });
        when(membershipRepository.findByFarmId(any())).thenReturn(List.of());
        lenient().when(membershipRepository.findByUser_Id(any(UUID.class))).thenReturn(List.of());
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.createFarm(request);

        assertThat(response).isNotNull();
        verify(membershipRepository, times(3)).save(any(UserFarmMembership.class));
    }

    @Test
    @DisplayName("Should reject assigning a scout who is already attached to another farm")
    void createFarm_WithScoutAttachedToAnotherFarm_ThrowsBadRequestException() {
        Farm otherFarm = Farm.builder()
                .id(UUID.randomUUID())
                .name("Other Farm")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STANDARD)
                .licensedAreaHectares(new BigDecimal("5.00"))
                .structureType(FarmStructureType.GREENHOUSE)
                .build();

        UserFarmMembership existingMembership = UserFarmMembership.builder()
                .user(scout)
                .farm(otherFarm)
                .role(Role.SCOUT)
                .isActive(true)
                .build();

        when(customerNumberService.normalizeCountryCode(createRequest.getCountry())).thenReturn("CA");
        when(userRepository.findById(farmOwner.getId())).thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId())).thenReturn(Optional.of(scout));
        when(farmRepository.findByNameIgnoreCase(createRequest.getName())).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> {
            Farm farm = invocation.getArgument(0);
            farm.setId(UUID.randomUUID());
            return farm;
        });
        when(membershipRepository.findByFarmId(any())).thenReturn(List.of());
        lenient().when(membershipRepository.findByUser_Id(any(UUID.class))).thenReturn(List.of());
        when(membershipRepository.findByUser_Id(scout.getId())).thenReturn(List.of(existingMembership));

        assertThatThrownBy(() -> farmService.createFarm(createRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Scouts can be assigned to only one farm");
    }

    @Test
    @DisplayName("Should throw ConflictException when farm name exists")
    void createFarm_WithExistingName_ThrowsConflictException() {
        // Arrange
        when(farmRepository.findByNameIgnoreCase(createRequest.getName()))
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
        when(customerNumberService.normalizeCountryCode(createRequest.getCountry()))
                .thenReturn("CA");
        when(farmRepository.findByNameIgnoreCase(createRequest.getName()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(createRequest.getOwnerId()))
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
        verify(farmAccessService).requireSuperAdmin();
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
    @DisplayName("SuperAdmin can update farm slug")
    void updateFarm_AsSuperAdmin_UpdatesSlug() {
        testFarm.setSlug("test-farm");
        UpdateFarmRequest slugRequest = new UpdateFarmRequest(
                null,
                "Acme Farm Portal",
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
                null
        );

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(farmRepository.findBySlug("acme-farm-portal")).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.isSuperAdmin()).thenReturn(true);
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.updateFarm(testFarm.getId(), slugRequest);

        assertThat(response.slug()).isEqualTo("acme-farm-portal");
        verify(farmRepository).save(argThat(farm -> "acme-farm-portal".equals(farm.getSlug())));
    }

    @Test
    @DisplayName("Update can replace farm member assignments")
    void updateFarm_WithMemberAssignments_ReplacesNonPrimaryMembers() {
        User priorMember = User.builder()
                .id(UUID.randomUUID())
                .email("old-member@example.com")
                .role(Role.MANAGER)
                .isEnabled(true)
                .build();

        UpdateFarmRequest membershipUpdate = new UpdateFarmRequest(
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
                List.of(new FarmMemberAssignmentRequest(managerMember.getId(), Role.MANAGER))
        );

        UserFarmMembership oldMembership = UserFarmMembership.builder()
                .user(priorMember)
                .farm(testFarm)
                .role(Role.MANAGER)
                .isActive(true)
                .build();
        UserFarmMembership ownerMembership = UserFarmMembership.builder()
                .user(farmOwner)
                .farm(testFarm)
                .role(Role.FARM_ADMIN)
                .isActive(true)
                .build();
        UserFarmMembership scoutMembership = UserFarmMembership.builder()
                .user(scout)
                .farm(testFarm)
                .role(Role.SCOUT)
                .isActive(true)
                .build();

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.isSuperAdmin()).thenReturn(true);
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);
        when(userRepository.findById(managerMember.getId())).thenReturn(Optional.of(managerMember));
        when(membershipRepository.findByFarmId(testFarm.getId())).thenReturn(List.of(oldMembership, ownerMembership, scoutMembership));

        farmService.updateFarm(testFarm.getId(), membershipUpdate);

        assertThat(oldMembership.getIsActive()).isFalse();
        verify(membershipRepository, atLeast(2)).save(any(UserFarmMembership.class));
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
    @DisplayName("Should get farm by slug successfully")
    void getFarmBySlug_WithValidSlug_ReturnsFarm() {
        testFarm.setSlug("test-farm");

        when(farmRepository.findBySlug("test-farm"))
                .thenReturn(Optional.of(testFarm));
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.getFarmBySlug("Test Farm");

        assertThat(response).isNotNull();
        assertThat(response.slug()).isEqualTo("test-farm");
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
    @DisplayName("SuperAdmin can permanently delete archived farms after 21 days when they have no historical records")
    void deleteFarmPermanently_WhenEligible_DeletesFarmAndConfigRows() {
        testFarm.setIsArchived(true);
        testFarm.setLicenseArchivedDate(LocalDate.now().minusDays(22));

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(scoutingSessionRepository.existsByFarmId(testFarm.getId())).thenReturn(false);
        when(farmLicenseHistoryRepository.existsByFarmId(testFarm.getId())).thenReturn(false);
        when(supplyOrderRequestRepository.existsByFarmId(testFarm.getId())).thenReturn(false);

        farmService.deleteFarmPermanently(testFarm.getId());

        verify(farmAccessService).requireSuperAdmin();
        verify(farmFeatureEntitlementRepository).deleteByFarmId(testFarm.getId());
        verify(customSpeciesDefinitionRepository).deleteByFarmId(testFarm.getId());
        verify(membershipRepository).deleteByFarmId(testFarm.getId());
        verify(farmRepository).delete(testFarm);
        verify(cacheService).evictFarmCachesAfterCommit(testFarm.getId());
    }

    @Test
    @DisplayName("Permanent delete rejects farms that are not archived")
    void deleteFarmPermanently_WhenFarmNotArchived_ThrowsBadRequestException() {
        testFarm.setIsArchived(false);

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));

        assertThatThrownBy(() -> farmService.deleteFarmPermanently(testFarm.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only archived farms");

        verify(farmRepository, never()).delete(any(Farm.class));
    }

    @Test
    @DisplayName("Permanent delete rejects farms archived less than 21 days ago")
    void deleteFarmPermanently_WhenArchivedTooRecently_ThrowsBadRequestException() {
        testFarm.setIsArchived(true);
        testFarm.setLicenseArchivedDate(LocalDate.now().minusDays(20));

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));

        assertThatThrownBy(() -> farmService.deleteFarmPermanently(testFarm.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least 21 days");

        verify(farmRepository, never()).delete(any(Farm.class));
    }

    @Test
    @DisplayName("Permanent delete rejects farms with sessions")
    void deleteFarmPermanently_WhenSessionsExist_ThrowsBadRequestException() {
        testFarm.setIsArchived(true);
        testFarm.setLicenseArchivedDate(LocalDate.now().minusDays(30));

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(scoutingSessionRepository.existsByFarmId(testFarm.getId())).thenReturn(true);

        assertThatThrownBy(() -> farmService.deleteFarmPermanently(testFarm.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("scouting sessions");

        verify(farmRepository, never()).delete(any(Farm.class));
    }

    @Test
    @DisplayName("Permanent delete rejects farms with generated license records")
    void deleteFarmPermanently_WhenLicenseReferenceExists_ThrowsBadRequestException() {
        testFarm.setIsArchived(true);
        testFarm.setLicenseArchivedDate(LocalDate.now().minusDays(30));
        testFarm.setLicenseReference("LIC-EXAMPLE");

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(scoutingSessionRepository.existsByFarmId(testFarm.getId())).thenReturn(false);

        assertThatThrownBy(() -> farmService.deleteFarmPermanently(testFarm.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("generated license records");

        verify(farmRepository, never()).delete(any(Farm.class));
    }

    @Test
    @DisplayName("Permanent delete rejects farms with supply order requests")
    void deleteFarmPermanently_WhenSupplyOrdersExist_ThrowsBadRequestException() {
        testFarm.setIsArchived(true);
        testFarm.setLicenseArchivedDate(LocalDate.now().minusDays(30));

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(scoutingSessionRepository.existsByFarmId(testFarm.getId())).thenReturn(false);
        when(farmLicenseHistoryRepository.existsByFarmId(testFarm.getId())).thenReturn(false);
        when(supplyOrderRequestRepository.existsByFarmId(testFarm.getId())).thenReturn(true);

        assertThatThrownBy(() -> farmService.deleteFarmPermanently(testFarm.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("supply order requests");

        verify(farmRepository, never()).delete(any(Farm.class));
    }

    @Test
    @DisplayName("Manager cannot update farm fields")
    void updateFarm_AsManager_ThrowsForbiddenException() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        doThrow(new ForbiddenException("Only super administrators can perform this action."))
                .when(farmAccessService).requireSuperAdmin();

        assertThatThrownBy(() -> farmService.updateFarm(testFarm.getId(), updateRequest))
                .isInstanceOf(ForbiddenException.class);
        verify(farmRepository, never()).save(any(Farm.class));
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
        when(customerNumberService.normalizeCountryCode(createRequest.getCountry()))
                .thenReturn("CA");
        when(userRepository.findById(farmOwner.getId()))
                .thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(farmRepository.findByNameIgnoreCase(createRequest.getName()))
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
                farm.getDefaultBayCount().equals(createRequest.getDefaultBayCount()) &&
                        farm.getDefaultBenchesPerBay().equals(createRequest.getDefaultBenchesPerBay()) &&
                        farm.getDefaultSpotChecksPerBench().equals(createRequest.getDefaultSpotChecksPerBench())
        ));
    }

    @Test
    @DisplayName("Should infer FIELD structure type from field blocks when type omitted")
    void createFarm_WithFieldBlocksAndNoStructureType_InfersField() {
        CreateFarmRequest fieldFarmRequest = CreateFarmRequest.builder()
                .name("Field Farm")
                .description("Open field setup")
                .address("123 Field Road")
                .city("Farmville")
                .province("Ontario")
                .postalCode("N1N 1N1")
                .country("Canada")
                .contactName("Field Ops")
                .contactEmail("field@example.com")
                .contactPhone("555-0000")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.BASIC)
                .billingEmail("billing@example.com")
                .licensedAreaHectares(new BigDecimal("20.00"))
                .licensedUnitQuota(50)
                .quotaDiscountPercentage(BigDecimal.ZERO)
                .defaultBayCount(9)
                .defaultBenchesPerBay(0)
                .defaultSpotChecksPerBench(4)
                .greenhouses(new ArrayList<>())
                .fieldBlocks(List.of(new CreateFieldBlockRequest("North Field", null, null, List.of("Row-1"), true)))
                .timezone("America/Toronto")
                .licenseExpiryDate(LocalDate.now().plusYears(1))
                .autoRenewEnabled(false)
                .build();

        when(customerNumberService.normalizeCountryCode(fieldFarmRequest.getCountry())).thenReturn("CA");
        when(farmRepository.findByNameIgnoreCase(fieldFarmRequest.getName())).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.createFarm(fieldFarmRequest);

        assertThat(response.structureType()).isEqualTo(FarmStructureType.FIELD);
        verify(farmRepository).save(argThat(farm ->
                farm.getStructureType() == FarmStructureType.FIELD
                        && farm.getFieldBlocks().size() == 1
                        && farm.getFieldBlocks().getFirst().getBayCount() == 0
                        && farm.getFieldBlocks().getFirst().getSpotChecksPerBay() == 4
        ));
    }

    @Test
    @DisplayName("Should generate bay and bench tags when farm structures omit them")
    void createFarm_WithoutStructureTags_GeneratesDefaults() {
        CreateFarmRequest request = CreateFarmRequest.builder()
                .name("Tagged Farm")
                .description("Generated layout tags")
                .address("123 Farm Road")
                .city("Farmville")
                .province("Ontario")
                .postalCode("N1N 1N1")
                .country("Canada")
                .ownerId(farmOwner.getId())
                .scoutId(scout.getId())
                .contactName("John Doe")
                .contactEmail("john@example.com")
                .contactPhone("555-1234")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STANDARD)
                .billingEmail("billing@example.com")
                .licensedAreaHectares(new BigDecimal("15.00"))
                .licensedUnitQuota(100)
                .quotaDiscountPercentage(new BigDecimal("5.00"))
                .structureType(FarmStructureType.GREENHOUSE)
                .defaultBayCount(4)
                .defaultBenchesPerBay(3)
                .defaultSpotChecksPerBench(2)
                .greenhouses(List.of(new CreateGreenhouseRequest("House 1", null, 2, 3, 2, List.of(), List.of(), new BigDecimal("1.50"))))
                .fieldBlocks(List.of(new CreateFieldBlockRequest("Field 1", 2, 2, List.of(), true, new BigDecimal("2.00"))))
                .timezone("America/Toronto")
                .licenseExpiryDate(LocalDate.now().plusYears(1))
                .autoRenewEnabled(true)
                .build();

        when(customerNumberService.normalizeCountryCode(request.getCountry())).thenReturn("CA");
        when(userRepository.findById(farmOwner.getId())).thenReturn(Optional.of(farmOwner));
        when(userRepository.findById(scout.getId())).thenReturn(Optional.of(scout));
        when(farmRepository.findByNameIgnoreCase(request.getName())).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.createFarm(request);

        assertThat(response.name()).isEqualTo("Tagged Farm");
        verify(farmRepository).save(argThat(farm ->
                farm.getGreenhouses().getFirst().getBayTags().equals(List.of("Bay 1", "Bay 2"))
                        && farm.getGreenhouses().getFirst().getBenchTags().equals(List.of("Bed 1", "Bed 2", "Bed 3"))
                        && farm.getFieldBlocks().getFirst().getBayTags().equals(List.of("Bay 1", "Bay 2"))
        ));
    }

}
