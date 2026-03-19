package mofo.com.pestscout.scouting.service;

import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.FieldBlockRepository;
import mofo.com.pestscout.farm.repository.GreenhouseRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.farm.service.LicenseService;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionTargetRepository;
import mofo.com.pestscout.scouting.repository.SessionAuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoutingSessionService Unit Tests")
class ScoutingSessionServiceTest {

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    @Mock
    private ScoutingSessionTargetRepository sessionTargetRepository;

    @Mock
    private SessionAuditEventRepository auditEventRepository;

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private FieldBlockRepository fieldBlockRepository;

    @Mock
    private GreenhouseRepository greenhouseRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FarmAccessService farmAccessService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFarmMembershipRepository membershipRepository;

    @Mock
    private LicenseService licenseService;

    @Mock
    private CacheService cacheService;

    @Mock
    private SessionAuditService sessionAuditService;

    @InjectMocks
    private ScoutingSessionService scoutingSessionService;

    private Farm testFarm;
    private User manager;
    private User scout;
    private User superAdmin;
    private Greenhouse greenhouse;
    private FieldBlock fieldBlock;
    private ScoutingSession testSession;
    private CreateScoutingSessionRequest createRequest;

    @BeforeEach
    void setUp() {
        manager = User.builder()
                .id(UUID.randomUUID())
                .email("manager@example.com")
                .firstName("Manager")
                .lastName("User")
                .role(Role.MANAGER)
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

        superAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .firstName("System")
                .lastName("Admin")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build();

        testFarm = Farm.builder()
                .id(UUID.randomUUID())
                .name("Test Farm")
                .owner(manager)
                .scout(scout)
                .defaultBayCount(5)
                .defaultBenchesPerBay(10)
                .defaultSpotChecksPerBench(3)
                .build();

        greenhouse = Greenhouse.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .name("Greenhouse 1")
                .bayCount(5)
                .benchesPerBay(10)
                .spotChecksPerBench(3)
                .areaHectares(new BigDecimal("5.00"))
                .build();

        fieldBlock = FieldBlock.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .name("Field Block 1")
                .bayCount(8)
                .areaHectares(new BigDecimal("4.00"))
                .build();

        testSession = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .manager(manager)
                .scout(scout)
                .sessionDate(LocalDate.now())
                .weekNumber(1)
                .cropType("Tomato")
                .cropVariety("Cherry")
                .status(SessionStatus.DRAFT)
                .observations(new ArrayList<>())
                .targets(new ArrayList<>())
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();

        SessionTargetRequest targetRequest = new SessionTargetRequest(
                greenhouse.getId(),
                null,
                true,
                true,
                List.of(),
                List.of()
        );

        createRequest = new CreateScoutingSessionRequest(
                testFarm.getId(),
                scout.getId(),
                List.of(targetRequest),
                LocalDate.now(),
                1,
                "Tomato",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.of(10, 30),
                "Sunny and warm",
                "Test notes",
                PhotoSourceType.SCOUT_HANDHELD,
                SessionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null
        );

        lenient().when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SCOUT);
        lenient().when(currentUserService.getCurrentUserId()).thenReturn(scout.getId());
        lenient().when(membershipRepository.findByUser_IdAndFarmId(scout.getId(), testFarm.getId()))
                .thenReturn(Optional.of(UserFarmMembership.builder()
                        .user(scout)
                        .farm(testFarm)
                        .role(Role.SCOUT)
                        .isActive(true)
                        .build()));
    }

    @Test
    @DisplayName("Should create session successfully")
    void createSession_WithValidData_CreatesSession() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmAccessService.isSuperAdmin())
                .thenReturn(false);
        when(currentUserService.getCurrentUser())
                .thenReturn(manager);
        when(userRepository.findById(scout.getId()))
                .thenReturn(Optional.of(scout));
        when(greenhouseRepository.findById(greenhouse.getId()))
                .thenReturn(Optional.of(greenhouse));
        when(licenseService.calculateSelectedAreaHectares(
                greenhouse.getAreaHectares(),
                greenhouse.resolvedBayCount(),
                true,
                greenhouse.resolvedBayCount(),
                greenhouse.getName()
        )).thenReturn(new BigDecimal("5.00"));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.createSession(createRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.farmId()).isEqualTo(testFarm.getId());
        assertThat(result.status()).isEqualTo(SessionStatus.NEW);
        verify(farmAccessService).requireAdminOrSuperAdmin(testFarm);
        verify(sessionRepository).save(any(ScoutingSession.class));
    }

    @Test
    @DisplayName("Should use explicit target hectares when provided")
    void createSession_WithExplicitTargetArea_UsesOverride() {
        SessionTargetRequest targetRequest = new SessionTargetRequest(
                greenhouse.getId(),
                null,
                true,
                true,
                List.of(),
                List.of(),
                new BigDecimal("1.25")
        );
        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                testFarm.getId(),
                scout.getId(),
                List.of(targetRequest),
                LocalDate.now(),
                1,
                "Tomato",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.of(10, 30),
                "Sunny and warm",
                "Test notes",
                PhotoSourceType.SCOUT_HANDHELD,
                SessionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null
        );

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(farmAccessService.isSuperAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUser()).thenReturn(manager);
        when(userRepository.findById(scout.getId())).thenReturn(Optional.of(scout));
        when(greenhouseRepository.findById(greenhouse.getId())).thenReturn(Optional.of(greenhouse));
        when(sessionRepository.save(any(ScoutingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingSessionDetailDto result = scoutingSessionService.createSession(request);

        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().getFirst().areaHectares()).isEqualByComparingTo("1.25");
        verify(licenseService, never()).calculateSelectedAreaHectares(any(), anyInt(), anyBoolean(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Should create session when scout and targets are omitted")
    void createSession_WithoutScoutIdAndTargets_UsesFarmDefaults() {
        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                testFarm.getId(),
                null,
                null,
                LocalDate.now(),
                1,
                "Tomato",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.of(10, 30),
                "Sunny and warm",
                "Test notes",
                PhotoSourceType.SCOUT_HANDHELD,
                SessionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null
        );

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmAccessService.isSuperAdmin())
                .thenReturn(false);
        when(currentUserService.getCurrentUser())
                .thenReturn(manager);
        when(greenhouseRepository.findByFarmId(testFarm.getId()))
                .thenReturn(List.of(greenhouse));
        when(fieldBlockRepository.findByFarmId(testFarm.getId()))
                .thenReturn(List.of(fieldBlock));
        when(licenseService.calculateSelectedAreaHectares(
                greenhouse.getAreaHectares(),
                greenhouse.resolvedBayCount(),
                true,
                greenhouse.resolvedBayCount(),
                greenhouse.getName()
        )).thenReturn(new BigDecimal("5.00"));
        when(licenseService.calculateSelectedAreaHectares(
                fieldBlock.getAreaHectares(),
                fieldBlock.resolvedBayCount(),
                true,
                fieldBlock.resolvedBayCount(),
                fieldBlock.getName()
        )).thenReturn(new BigDecimal("4.00"));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingSessionDetailDto result = scoutingSessionService.createSession(request);

        assertThat(result).isNotNull();
        assertThat(result.scoutId()).isEqualTo(scout.getId());
        assertThat(result.sections()).hasSize(2);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should allow creating an unassigned session when farm has no scout")
    void createSession_WithoutAnyScout_AssignsNoScout() {
        testFarm.setScout(null);
        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                testFarm.getId(),
                null,
                List.of(),
                LocalDate.now(),
                1,
                "Tomato",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.of(10, 30),
                "Sunny and warm",
                "Test notes",
                PhotoSourceType.SCOUT_HANDHELD,
                SessionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null
        );

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmAccessService.isSuperAdmin())
                .thenReturn(false);
        when(currentUserService.getCurrentUser())
                .thenReturn(manager);
        when(greenhouseRepository.findByFarmId(testFarm.getId()))
                .thenReturn(List.of(greenhouse));
        when(fieldBlockRepository.findByFarmId(testFarm.getId()))
                .thenReturn(List.of());
        when(licenseService.calculateSelectedAreaHectares(
                greenhouse.getAreaHectares(),
                greenhouse.resolvedBayCount(),
                true,
                greenhouse.resolvedBayCount(),
                greenhouse.getName()
        )).thenReturn(new BigDecimal("5.00"));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingSessionDetailDto result = scoutingSessionService.createSession(request);

        assertThat(result).isNotNull();
        assertThat(result.scoutId()).isNull();
        assertThat(result.sections()).hasSize(1);
    }

    @Test
    @DisplayName("Should reuse a session as an empty editable draft")
    void reuseSession_WithExistingSession_CreatesEmptyDraftCopy() {
        testSession.setStatus(SessionStatus.COMPLETED);
        testSession.setTemperatureCelsius(new BigDecimal("23.5"));
        testSession.setRelativeHumidityPercent(new BigDecimal("61.0"));
        testSession.setObservationTime(LocalTime.of(9, 45));
        testSession.setWeatherNotes("Warm and clear");
        testSession.setNotes("Carry over planning notes");
        testSession.setSurveySpecies(new ArrayList<>(List.of(SpeciesCode.WHITEFLIES, SpeciesCode.THRIPS)));
        testSession.setDefaultPhotoSourceType(PhotoSourceType.DRONE);

        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .greenhouse(greenhouse)
                .includeAllBays(true)
                .includeAllBenches(true)
                .areaHectares(new BigDecimal("1.50"))
                .build();
        testSession.addTarget(target);

        ScoutingObservation observation = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.WHITEFLIES)
                .bayIndex(1)
                .bayLabel("Bay-1")
                .benchIndex(1)
                .benchLabel("Bed-1")
                .spotIndex(1)
                .count(4)
                .notes("Observed whiteflies")
                .build();
        testSession.addObservation(observation);

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(farmAccessService.isSuperAdmin())
                .thenReturn(false);
        when(currentUserService.getCurrentUser())
                .thenReturn(manager);
        when(greenhouseRepository.findById(greenhouse.getId()))
                .thenReturn(Optional.of(greenhouse));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> {
                    ScoutingSession saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setVersion(1L);
                    return saved;
                });

        ScoutingSessionDetailDto result = scoutingSessionService.reuseSession(testSession.getId());

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotEqualTo(testSession.getId());
        assertThat(result.status()).isEqualTo(SessionStatus.DRAFT);
        assertThat(result.managerId()).isEqualTo(manager.getId());
        assertThat(result.scoutId()).isEqualTo(scout.getId());
        assertThat(result.temperatureCelsius()).isNull();
        assertThat(result.relativeHumidityPercent()).isNull();
        assertThat(result.observationTime()).isNull();
        assertThat(result.weatherNotes()).isNull();
        assertThat(result.notes()).isEqualTo("Carry over planning notes");
        assertThat(result.surveySpeciesCodes()).containsExactly(SpeciesCode.WHITEFLIES, SpeciesCode.THRIPS);
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().getFirst().observations()).isEmpty();

        verify(sessionAuditService).record(
                any(ScoutingSession.class),
                eq(SessionAuditAction.SESSION_REUSED),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when farm not found")
    void createSession_WithInvalidFarm_ThrowsResourceNotFoundException() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.createSession(createRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Farm");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject assigning a scout who does not belong to the session farm")
    void createSession_WithScoutOutsideFarm_ThrowsBadRequestException() {
        User outsideScout = User.builder()
                .id(UUID.randomUUID())
                .email("outside-scout@example.com")
                .firstName("Outside")
                .lastName("Scout")
                .role(Role.SCOUT)
                .isEnabled(true)
                .build();

        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                testFarm.getId(),
                outsideScout.getId(),
                createRequest.targets(),
                createRequest.sessionDate(),
                createRequest.weekNumber(),
                createRequest.crop(),
                createRequest.variety(),
                createRequest.temperatureCelsius(),
                createRequest.relativeHumidityPercent(),
                createRequest.observationTime(),
                createRequest.weatherNotes(),
                createRequest.notes(),
                createRequest.defaultPhotoSourceType(),
                createRequest.status(),
                createRequest.deviceId(),
                createRequest.deviceType(),
                createRequest.location(),
                createRequest.comment(),
                createRequest.actorName()
        );

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(farmAccessService.isSuperAdmin())
                .thenReturn(false);
        when(currentUserService.getCurrentUser())
                .thenReturn(manager);
        when(userRepository.findById(outsideScout.getId()))
                .thenReturn(Optional.of(outsideScout));
        when(membershipRepository.findByUser_IdAndFarmId(outsideScout.getId(), testFarm.getId()))
                .thenReturn(Optional.empty());
        when(greenhouseRepository.findById(greenhouse.getId()))
                .thenReturn(Optional.of(greenhouse));
        when(licenseService.calculateSelectedAreaHectares(
                greenhouse.getAreaHectares(),
                greenhouse.resolvedBayCount(),
                true,
                greenhouse.resolvedBayCount(),
                greenhouse.getName()
        )).thenReturn(new BigDecimal("5.00"));

        assertThatThrownBy(() -> scoutingSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("same farm");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update session successfully")
    void updateSession_WithValidData_UpdatesSession() {
        // Arrange
        UpdateScoutingSessionRequest updateRequest = new UpdateScoutingSessionRequest(
                LocalDate.now().plusDays(1),
                2,
                null,
                null,
                "Updated Crop",
                "Updated Variety",
                new BigDecimal("25.0"),
                null,
                null,
                null,
                "Updated notes",
                PhotoSourceType.DRONE,
                null,
                1L,
                null,
                null,
                null,
                null,
                null
        );
        testSession.setVersion(1L);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.updateSession(
                testSession.getId(),
                updateRequest
        );

        // Assert
        assertThat(result).isNotNull();
        verify(farmAccessService).requireAdminOrSuperAdmin(testFarm);
        verify(sessionRepository).save(any(ScoutingSession.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when updating completed session")
    void updateSession_WithCompletedSession_ThrowsBadRequestException() {
        // Arrange
        testSession.setStatus(SessionStatus.COMPLETED);
        UpdateScoutingSessionRequest updateRequest = new UpdateScoutingSessionRequest(
                LocalDate.now(),
                1,
                null,
                null,
                "Crop",
                "Variety",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null
        );

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.updateSession(
                testSession.getId(),
                updateRequest
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reopened");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should promote a completed planning draft to new when scout and target are assigned")
    void updateSession_WithDraftAssigningScoutAndTarget_PromotesToNew() {
        testSession.setVersion(1L);
        testSession.setScout(null);
        testSession.getTargets().clear();

        SessionTargetRequest targetRequest = new SessionTargetRequest(
                greenhouse.getId(),
                null,
                true,
                true,
                List.of(),
                List.of()
        );

        UpdateScoutingSessionRequest updateRequest = new UpdateScoutingSessionRequest(
                null,
                null,
                List.of(targetRequest),
                scout.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null
        );

        when(sessionRepository.findById(testSession.getId())).thenReturn(Optional.of(testSession));
        when(userRepository.findById(scout.getId())).thenReturn(Optional.of(scout));
        when(greenhouseRepository.findById(greenhouse.getId())).thenReturn(Optional.of(greenhouse));
        when(sessionRepository.save(any(ScoutingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingSessionDetailDto result = scoutingSessionService.updateSession(testSession.getId(), updateRequest);

        assertThat(result.status()).isEqualTo(SessionStatus.NEW);
        assertThat(result.scoutId()).isEqualTo(scout.getId());
        assertThat(result.sections()).hasSize(1);
    }

    @Test
    @DisplayName("Should delete draft session")
    void deleteSession_WithDraftStatus_DeletesSession() {
        when(sessionRepository.findById(testSession.getId())).thenReturn(Optional.of(testSession));

        scoutingSessionService.deleteSession(testSession.getId());

        verify(farmAccessService).requireAdminOrSuperAdmin(testFarm);
        verify(sessionRepository).delete(testSession);
    }

    @Test
    @DisplayName("Should reject deleting in-progress session")
    void deleteSession_WithInProgressStatus_ThrowsBadRequestException() {
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        when(sessionRepository.findById(testSession.getId())).thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> scoutingSessionService.deleteSession(testSession.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("draft or new");

        verify(sessionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should start session successfully")
    void startSession_WithDraftSession_StartsSession() {
        // Arrange
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId())
                .thenReturn(scout.getId());
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.findByFarmIdAndScoutIdAndStatus(
                testFarm.getId(),
                scout.getId(),
                SessionStatus.IN_PROGRESS
        )).thenReturn(List.of());
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.startSession(testSession.getId());

        // Assert
        assertThat(result).isNotNull();
        verify(sessionRepository).save(argThat(session ->
                session.getStatus() == SessionStatus.IN_PROGRESS &&
                        session.getStartedAt() != null
        ));
    }

    @Test
    @DisplayName("Should reject manager from starting a session")
    void startSession_WithManagerRole_ThrowsForbiddenException() {
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> scoutingSessionService.startSession(testSession.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("assigned scout can start");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject super admin from starting a session directly")
    void startSession_WithSuperAdminRole_ThrowsForbiddenException() {
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> scoutingSessionService.startSession(testSession.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("assigned scout can start");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw BadRequestException when starting completed session")
    void startSession_WithCompletedSession_ThrowsBadRequestException() {
        // Arrange
        testSession.setStatus(SessionStatus.COMPLETED);
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId())
                .thenReturn(scout.getId());
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.startSession(testSession.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("submitted or completed");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should request remote start consent for assigned scout")
    void requestRemoteStart_WithSuperAdmin_CreatesPendingConsent() {
        testSession.setVersion(2L);
        RemoteStartSessionRequest request = new RemoteStartSessionRequest(
                2L,
                "Scout requested assistance",
                null,
                null,
                null,
                "System Admin"
        );

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);
        when(currentUserService.getCurrentUser())
                .thenReturn(superAdmin);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingSessionDetailDto result = scoutingSessionService.requestRemoteStart(testSession.getId(), request);

        assertThat(result.remoteStartConsentRequired()).isTrue();
        assertThat(result.remoteStartRequestedByName()).isEqualTo("System Admin");
        verify(sessionRepository).save(argThat(session ->
                session.isRemoteStartPending()
                        && session.getRemoteStartRequestedAt() != null
                        && "System Admin".equals(session.getRemoteStartRequestedByName())
                        && session.getStatus() == SessionStatus.DRAFT
        ));
    }

    @Test
    @DisplayName("Should allow assigned scout to accept remote start and begin session")
    void acceptRemoteStart_WithAssignedScout_StartsSession() {
        testSession.setVersion(2L);
        testSession.requestRemoteStart(superAdmin.getId(), "System Admin");
        AcceptRemoteStartRequest request = new AcceptRemoteStartRequest(
                2L,
                "Accepted remote accessed session start",
                null,
                null,
                null,
                "Scout User"
        );

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId())
                .thenReturn(scout.getId());
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.findByFarmIdAndScoutIdAndStatus(
                testFarm.getId(),
                scout.getId(),
                SessionStatus.IN_PROGRESS
        )).thenReturn(List.of());
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingSessionDetailDto result = scoutingSessionService.acceptRemoteStart(testSession.getId(), request);

        assertThat(result.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(result.remoteStartConsentRequired()).isFalse();
        verify(sessionRepository).save(argThat(session ->
                session.getStatus() == SessionStatus.IN_PROGRESS
                        && session.getStartedAt() != null
                        && !session.isRemoteStartPending()
        ));
    }

    @Test
    @DisplayName("Should reject manager changing session status to in progress via update")
    void updateSession_WithManagerChangingStatusToInProgress_ThrowsBadRequestException() {
        UpdateScoutingSessionRequest updateRequest = new UpdateScoutingSessionRequest(
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
                SessionStatus.IN_PROGRESS,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> scoutingSessionService.updateSession(testSession.getId(), updateRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid session transition");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should complete session with acknowledgment")
    void completeSession_WithAcknowledgment_CompletesSession() {
        // Arrange
        testSession.setStatus(SessionStatus.SUBMITTED);
        testSession.setVersion(1L);
        CompleteSessionRequest request = new CompleteSessionRequest(1L, true, null, null, null, null, "Scout User");

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId())
                .thenReturn(scout.getId());
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.completeSession(
                testSession.getId(),
                request
        );

        // Assert
        assertThat(result).isNotNull();
        verify(sessionRepository).save(argThat(session ->
                session.getStatus() == SessionStatus.COMPLETED &&
                        session.getCompletedAt() != null &&
                        session.isConfirmationAcknowledged()
        ));
    }

    @Test
    @DisplayName("Should allow assigned scout to complete an in-progress session")
    void completeSession_WithAssignedScout_CompletesSession() {
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        testSession.setVersion(1L);
        CompleteSessionRequest request = new CompleteSessionRequest(1L, true, null, null, null, null, "Scout User");

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId())
                .thenReturn(scout.getId());
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingSessionDetailDto result = scoutingSessionService.completeSession(
                testSession.getId(),
                request
        );

        assertThat(result).isNotNull();
        verify(sessionRepository).save(argThat(session ->
                session.getStatus() == SessionStatus.COMPLETED &&
                        session.getCompletedAt() != null &&
                        session.isConfirmationAcknowledged()
        ));
        verify(farmAccessService, never()).requireAdminOrSuperAdmin(any());
    }

    @Test
    @DisplayName("Should reject manager from completing a session")
    void completeSession_WithManagerRole_ThrowsForbiddenException() {
        testSession.setStatus(SessionStatus.SUBMITTED);
        testSession.setVersion(1L);
        CompleteSessionRequest request = new CompleteSessionRequest(1L, true, null, null, null, null, "Manager User");

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> scoutingSessionService.completeSession(testSession.getId(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("assigned scout can complete");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw BadRequestException when completing without acknowledgment")
    void completeSession_WithoutAcknowledgment_ThrowsBadRequestException() {
        // Arrange
        testSession.setStatus(SessionStatus.SUBMITTED);
        testSession.setVersion(1L);
        CompleteSessionRequest request = new CompleteSessionRequest(1L, false, null, null, null, null, "Scout User");

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId())
                .thenReturn(scout.getId());
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.completeSession(
                testSession.getId(),
                request
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("confirm");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reopen completed session")
    void reopenSession_WithCompletedSession_ReopensSession() {
        // Arrange
        testSession.setStatus(SessionStatus.COMPLETED);
        testSession.setConfirmationAcknowledged(true);

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.reopenSession(
                testSession.getId(),
                new ReopenSessionRequest("Reopen for edits", null, null, null, "Manager User")
        );

        // Assert
        assertThat(result).isNotNull();
        verify(sessionRepository).save(argThat(session ->
                session.getStatus() == SessionStatus.REOPENED &&
                        !session.isConfirmationAcknowledged() &&
                        session.getCompletedAt() == null &&
                        "Reopen for edits".equals(session.getReopenComment())
        ));
    }

    @Test
    @DisplayName("Should throw BadRequestException when reopening non-completed session")
    void reopenSession_WithDraftSession_ThrowsBadRequestException() {
        // Arrange
        testSession.setStatus(SessionStatus.DRAFT);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.reopenSession(
                testSession.getId(),
                new ReopenSessionRequest("Reopen for edits", null, null, null, "Manager User")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only completed sessions");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should upsert observation successfully")
    void upsertObservation_WithNewObservation_CreatesObservation() {
        // Arrange
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .greenhouse(greenhouse)
                .includeAllBays(true)
                .includeAllBenches(true)
                .build();

        UpsertObservationRequest request = new UpsertObservationRequest(
                testSession.getId(),
                target.getId(),
                SpeciesCode.THRIPS,
                1,
                "Bay-1",
                1,
                "Bench-1",
                1,
                5,
                "Test observation",
                null,
                1L
        );

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionTargetRepository.findByIdAndSessionId(target.getId(), testSession.getId()))
                .thenReturn(Optional.of(target));
        when(observationRepository.findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                any(), any(), any(), any(), any(), any()
        ))
                .thenReturn(Optional.empty());
        when(observationRepository.save(any(ScoutingObservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ScoutingObservationDto result = scoutingSessionService.upsertObservation(
                testSession.getId(),
                request
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.count()).isEqualTo(5);
        verify(observationRepository).save(any(ScoutingObservation.class));
    }

    @Test
    @DisplayName("Should allow scout to edit an incomplete session")
    void upsertObservation_WithIncompleteSession_CreatesObservation() {
        testSession.setStatus(SessionStatus.INCOMPLETE);
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .greenhouse(greenhouse)
                .includeAllBays(true)
                .includeAllBenches(true)
                .build();

        UpsertObservationRequest request = new UpsertObservationRequest(
                testSession.getId(),
                target.getId(),
                SpeciesCode.THRIPS,
                1,
                "Bay-1",
                1,
                "Bench-1",
                1,
                2,
                "Incomplete session edit",
                null,
                1L
        );

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionTargetRepository.findByIdAndSessionId(target.getId(), testSession.getId()))
                .thenReturn(Optional.of(target));
        when(observationRepository.findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(observationRepository.save(any(ScoutingObservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingObservationDto result = scoutingSessionService.upsertObservation(testSession.getId(), request);

        assertThat(result).isNotNull();
        assertThat(result.count()).isEqualTo(2);
        verify(observationRepository).save(any(ScoutingObservation.class));
    }

    @Test
    @DisplayName("Should update existing observation")
    void upsertObservation_WithExistingObservation_UpdatesObservation() {
        // Arrange
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .greenhouse(greenhouse)
                .includeAllBays(true)
                .includeAllBenches(true)
                .build();

        ScoutingObservation existingObservation = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(3)
                .build();

        UpsertObservationRequest request = new UpsertObservationRequest(
                testSession.getId(),
                target.getId(),
                SpeciesCode.THRIPS,
                1,
                "Bay-1",
                1,
                "Bench-1",
                1,
                10,
                "Updated observation",
                UUID.randomUUID(),
                1L
        );
        existingObservation.setVersion(1L);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionTargetRepository.findByIdAndSessionId(target.getId(), testSession.getId()))
                .thenReturn(Optional.of(target));
        when(observationRepository.findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                any(), any(), any(), any(), any(), any()
        ))
                .thenReturn(Optional.of(existingObservation));
        when(observationRepository.save(any(ScoutingObservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ScoutingObservationDto result = scoutingSessionService.upsertObservation(
                testSession.getId(),
                request
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.count()).isEqualTo(10);
        verify(observationRepository).save(argThat(obs ->
                obs.getCount() == 10 && "Updated observation".equals(obs.getNotes())
        ));
    }

    @Test
    @DisplayName("Should throw BadRequestException when upserting to completed session")
    void upsertObservation_WithCompletedSession_ThrowsBadRequestException() {
        // Arrange
        testSession.setStatus(SessionStatus.COMPLETED);
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .build();

        UpsertObservationRequest request = new UpsertObservationRequest(
                testSession.getId(),
                target.getId(),
                SpeciesCode.THRIPS,
                1,
                null,
                1,
                null,
                1,
                5,
                null,
                null,
                1L
        );

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        lenient().when(sessionTargetRepository.findByIdAndSessionId(target.getId(), testSession.getId()))
                .thenReturn(Optional.of(target));

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.upsertObservation(
                testSession.getId(),
                request
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cannot be edited by scout");

        verify(observationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete observation successfully")
    void deleteObservation_WithValidObservation_DeletesObservation() {
        // Arrange
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        ScoutingObservation observation = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .sessionTarget(ScoutingSessionTarget.builder().id(UUID.randomUUID()).session(testSession).build())
                .build();
        testSession.addObservation(observation);

        when(observationRepository.findByIdAndSessionId(observation.getId(), testSession.getId()))
                .thenReturn(Optional.of(observation));

        // Act
        scoutingSessionService.deleteObservation(testSession.getId(), observation.getId());

        // Assert
        verify(observationRepository).save(argThat(saved -> saved.isDeleted() && saved.getDeletedAt() != null));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when observation not found")
    void deleteObservation_WithInvalidId_ThrowsResourceNotFoundException() {
        // Arrange
        UUID observationId = UUID.randomUUID();
        when(observationRepository.findByIdAndSessionId(observationId, testSession.getId()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.deleteObservation(
                testSession.getId(),
                observationId
        ))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(observationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject bulk payload with mismatched session id")
    void bulkUpsertObservations_WithMismatchedSession_ThrowsBadRequest() {
        BulkUpsertObservationsRequest request = new BulkUpsertObservationsRequest(
                UUID.randomUUID(),
                List.of()
        );

        assertThatThrownBy(() -> scoutingSessionService.bulkUpsertObservations(testSession.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bulk payload does not match session");
    }

    @Test
    @DisplayName("Should perform bulk upsert and return observations")
    void bulkUpsertObservations_WithValidPayload_ProcessesAll() {
        // Arrange
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .includeAllBays(true)
                .includeAllBenches(true)
                .build();

        UpsertObservationRequest obsRequest = new UpsertObservationRequest(
                testSession.getId(),
                target.getId(),
                SpeciesCode.THRIPS,
                0,
                "Bay-0",
                0,
                "Bench-0",
                0,
                2,
                "Bulk",
                null,
                1L
        );

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionTargetRepository.findByIdAndSessionId(target.getId(), testSession.getId()))
                .thenReturn(Optional.of(target));
        when(observationRepository.findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                any(), any(), any(), any(), any(), any()
        ))
                .thenReturn(Optional.empty());
        when(observationRepository.save(any(ScoutingObservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BulkUpsertObservationsRequest bulkRequest = new BulkUpsertObservationsRequest(
                testSession.getId(),
                List.of(obsRequest)
        );

        // Act
        List<ScoutingObservationDto> results = scoutingSessionService.bulkUpsertObservations(testSession.getId(), bulkRequest);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().count()).isEqualTo(2);
        verify(observationRepository, times(1)).save(any(ScoutingObservation.class));
    }

    @Test
    @DisplayName("Should enforce idempotency across sessions")
    void upsertObservation_WithDuplicateClientRequestInOtherSession_ThrowsConflict() {
        // Arrange
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .includeAllBays(true)
                .includeAllBenches(true)
                .build();

        UUID requestId = UUID.randomUUID();
        UpsertObservationRequest request = new UpsertObservationRequest(
                testSession.getId(),
                target.getId(),
                SpeciesCode.WHITEFLIES,
                1,
                "Bay-1",
                1,
                "Bench-1",
                1,
                4,
                "note",
                requestId,
                1L
        );

        ScoutingSession otherSession = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .manager(manager)
                .scout(scout)
                .observations(new ArrayList<>())
                .targets(new ArrayList<>())
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();

        ScoutingObservation existing = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(otherSession)
                .clientRequestId(requestId)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.WHITEFLIES)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(3)
                .build();

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(observationRepository.findByClientRequestId(requestId))
                .thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.upsertObservation(testSession.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Idempotency key already used for another session");
    }

    @Test
    @DisplayName("Should reject stale version when updating observation")
    void upsertObservation_WithStaleVersion_ThrowsConflict() {
        // Arrange
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .includeAllBays(true)
                .includeAllBenches(true)
                .build();

        ScoutingObservation existing = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(1)
                .build();
        existing.setVersion(5L);

        UpsertObservationRequest request = new UpsertObservationRequest(
                testSession.getId(),
                target.getId(),
                SpeciesCode.THRIPS,
                1,
                "Bay-1",
                1,
                "Bench-1",
                1,
                2,
                "stale",
                UUID.randomUUID(),
                1L
        );

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionTargetRepository.findByIdAndSessionId(target.getId(), testSession.getId()))
                .thenReturn(Optional.of(target));
        when(observationRepository.findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                any(), any(), any(), any(), any(), any()
        ))
                .thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.upsertObservation(testSession.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("has changed on the server");
    }

    @Test
    @DisplayName("Should return changed sessions and observations since timestamp")
    void syncChanges_WithUpdates_ReturnsOnlyChangedRecords() {
        // Arrange
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        ScoutingSession updatedSession = testSession;
        updatedSession.setUpdatedAt(LocalDateTime.now());

        ScoutingObservation changedObs = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .sessionTarget(ScoutingSessionTarget.builder().id(UUID.randomUUID()).session(testSession).build())
                .speciesCode(SpeciesCode.WHITEFLIES)
                .bayIndex(0)
                .benchIndex(0)
                .spotIndex(0)
                .count(2)
                .build();
        changedObs.setUpdatedAt(LocalDateTime.now());

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);
        when(sessionRepository.findByFarmIdAndUpdatedAtAfter(testFarm.getId(), since))
                .thenReturn(List.of(updatedSession));
        when(sessionRepository.findByFarmId(testFarm.getId()))
                .thenReturn(List.of(testSession));
        when(observationRepository.findBySessionIdInAndUpdatedAtAfter(anyList(), eq(since)))
                .thenReturn(List.of(changedObs));
        when(sessionRepository.findAllById(anyIterable())).thenReturn(List.of(updatedSession));

        // Act
        ScoutingSyncResponse response = scoutingSessionService.syncChanges(testFarm.getId(), since, false);

        // Assert
        assertThat(response.sessions()).hasSize(1);
        assertThat(response.observations()).hasSize(1);
        assertThat(response.observations().getFirst().deleted()).isFalse();
    }

    @Test
    @DisplayName("Should include soft-deleted observations when requested")
    void syncChanges_WithIncludeDeleted_ReturnsDeletedObservations() {
        // Arrange
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        ScoutingObservation deletedObs = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .sessionTarget(ScoutingSessionTarget.builder().id(UUID.randomUUID()).session(testSession).build())
                .speciesCode(SpeciesCode.WHITEFLIES)
                .bayIndex(0)
                .benchIndex(0)
                .spotIndex(0)
                .count(2)
                .build();
        deletedObs.markDeleted();
        deletedObs.setUpdatedAt(LocalDateTime.now());

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);
        when(sessionRepository.findByFarmIdAndUpdatedAtAfter(testFarm.getId(), since))
                .thenReturn(List.of());
        when(sessionRepository.findByFarmId(testFarm.getId()))
                .thenReturn(List.of(testSession));
        when(observationRepository.findBySessionIdInAndUpdatedAtAfter(anyList(), eq(since)))
                .thenReturn(List.of(deletedObs));
        when(sessionRepository.findAllById(anyIterable())).thenReturn(List.of(testSession));

        // Act
        ScoutingSyncResponse response = scoutingSessionService.syncChanges(testFarm.getId(), since, true);

        // Assert
        assertThat(response.observations()).hasSize(1);
        assertThat(response.observations().getFirst().deleted()).isTrue();
    }

    @Test
    @DisplayName("Should get session successfully")
    void getSession_WithValidId_ReturnsSession() {
        // Arrange
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.getSession(testSession.getId());

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testSession.getId());
        verify(sessionRepository).findById(testSession.getId());
    }

    @Test
    @DisplayName("Should get session with audit metadata and record audit event")
    void getSession_WithAuditMetadata_ReturnsSessionAndRecordsAudit() {
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        ScoutingSessionDetailDto result = scoutingSessionService.getSession(
                testSession.getId(),
                "device-1",
                "web",
                "office",
                "System Admin"
        );

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testSession.getId());
        verify(sessionAuditService).record(
                eq(testSession),
                eq(SessionAuditAction.SESSION_VIEWED),
                isNull(),
                eq("device-1"),
                eq("web"),
                eq("office"),
                eq("System Admin")
        );
    }

    @Test
    @DisplayName("Should allow scout to view a draft when remote start consent is pending")
    void getSession_WithPendingRemoteStartDraft_AllowsAssignedScout() {
        testSession.setStatus(SessionStatus.DRAFT);
        testSession.requestRemoteStart(superAdmin.getId(), "System Admin");

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId())
                .thenReturn(scout.getId());
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        ScoutingSessionDetailDto result = scoutingSessionService.getSession(testSession.getId());

        assertThat(result).isNotNull();
        assertThat(result.remoteStartConsentRequired()).isTrue();
        assertThat(result.remoteStartRequestedByName()).isEqualTo("System Admin");
    }

    @Test
    @DisplayName("Should hide in-progress session details from super admin")
    void getSession_WithInProgressSessionAndSuperAdmin_ThrowsForbiddenException() {
        testSession.setStatus(SessionStatus.IN_PROGRESS);

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> scoutingSessionService.getSession(testSession.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("only visible to the assigned scout");
    }

    @Test
    @DisplayName("Should block manager from opening in-progress session details on their farm")
    void getSession_WithInProgressSessionAndManager_ThrowsForbiddenException() {
        testSession.setStatus(SessionStatus.IN_PROGRESS);

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);
        when(currentUserService.getCurrentUserId())
                .thenReturn(manager.getId());
        when(membershipRepository.existsByUser_IdAndFarmId(manager.getId(), testFarm.getId()))
                .thenReturn(true);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> scoutingSessionService.getSession(testSession.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("visible in the list");
    }

    @Test
    @DisplayName("Should block manager from viewing audit trail of in-progress session")
    void listAuditTrail_WithInProgressSessionAndManager_ThrowsForbiddenException() {
        testSession.setStatus(SessionStatus.IN_PROGRESS);

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);
        when(currentUserService.getCurrentUserId())
                .thenReturn(manager.getId());
        when(membershipRepository.existsByUser_IdAndFarmId(manager.getId(), testFarm.getId()))
                .thenReturn(true);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> scoutingSessionService.listAuditTrail(testSession.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("visible in the list");
    }

    @Test
    @DisplayName("Should list sessions for farm")
    void listSessions_WithValidFarm_ReturnsSessions() {
        // Arrange
        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmId(testFarm.getId()))
                .thenReturn(Arrays.asList(testSession));

        // Act
        List<ScoutingSessionDetailDto> result = scoutingSessionService.listSessions(testFarm.getId());

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
        verify(sessionRepository).findByFarmId(testFarm.getId());
    }

    @Test
    @DisplayName("Should hide live sessions from super admin list")
    void listSessions_WithSuperAdmin_FiltersOutLiveSessions() {
        ScoutingSession inProgressSession = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .manager(manager)
                .scout(scout)
                .sessionDate(LocalDate.now())
                .weekNumber(2)
                .cropType("Tomato")
                .cropVariety("Roma")
                .status(SessionStatus.IN_PROGRESS)
                .observations(new ArrayList<>())
                .targets(new ArrayList<>())
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.SUPER_ADMIN);
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmId(testFarm.getId()))
                .thenReturn(List.of(testSession, inProgressSession));

        List<ScoutingSessionDetailDto> result = scoutingSessionService.listSessions(testFarm.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(testSession.getId());
    }

    @Test
    @DisplayName("Should keep in-progress sessions visible but redacted for manager list")
    void listSessions_WithManager_RedactsInProgressSessions() {
        ScoutingSession inProgressSession = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .manager(manager)
                .scout(scout)
                .sessionDate(LocalDate.now())
                .weekNumber(2)
                .cropType("Tomato")
                .cropVariety("Roma")
                .notes("Scout only notes")
                .status(SessionStatus.IN_PROGRESS)
                .observations(new ArrayList<>())
                .targets(new ArrayList<>())
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();

        when(farmAccessService.getCurrentUserRole())
                .thenReturn(Role.MANAGER);
        when(currentUserService.getCurrentUserId())
                .thenReturn(manager.getId());
        when(membershipRepository.existsByUser_IdAndFarmId(manager.getId(), testFarm.getId()))
                .thenReturn(true);
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmId(testFarm.getId()))
                .thenReturn(List.of(testSession, inProgressSession));

        List<ScoutingSessionDetailDto> result = scoutingSessionService.listSessions(testFarm.getId());

        assertThat(result).hasSize(2);
        ScoutingSessionDetailDto redacted = result.stream()
                .filter(session -> session.id().equals(inProgressSession.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(redacted.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(redacted.notes()).isNull();
        assertThat(redacted.sections()).isEmpty();
        assertThat(redacted.recommendations()).isEmpty();
    }

    @Test
    @DisplayName("Should redact in-progress session sync payloads for manager")
    void syncChanges_WithManagerAndInProgressSession_RedactsSessionAndObservations() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        testSession.setUpdatedAt(LocalDateTime.now());

        ScoutingObservation changedObs = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .sessionTarget(ScoutingSessionTarget.builder().id(UUID.randomUUID()).session(testSession).build())
                .speciesCode(SpeciesCode.WHITEFLIES)
                .bayIndex(0)
                .benchIndex(0)
                .spotIndex(0)
                .count(2)
                .build();
        changedObs.setUpdatedAt(LocalDateTime.now());

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.MANAGER);
        when(currentUserService.getCurrentUserId()).thenReturn(manager.getId());
        when(membershipRepository.existsByUser_IdAndFarmId(manager.getId(), testFarm.getId())).thenReturn(true);
        when(sessionRepository.findByFarmIdAndUpdatedAtAfter(testFarm.getId(), since)).thenReturn(List.of(testSession));
        when(sessionRepository.findByFarmId(testFarm.getId())).thenReturn(List.of(testSession));
        when(observationRepository.findBySessionIdInAndUpdatedAtAfter(anyList(), eq(since))).thenReturn(List.of(changedObs));
        when(sessionRepository.findAllById(anyIterable())).thenReturn(List.of(testSession));

        ScoutingSyncResponse response = scoutingSessionService.syncChanges(testFarm.getId(), since, false);

        assertThat(response.sessions()).hasSize(1);
        assertThat(response.sessions().getFirst().status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(response.sessions().getFirst().sections()).isEmpty();
        assertThat(response.sessions().getFirst().notes()).isNull();
        assertThat(response.observations()).isEmpty();
    }
}
