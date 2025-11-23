package mofo.com.pestscout.scouting;

import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.FieldBlockRepository;
import mofo.com.pestscout.farm.repository.GreenhouseRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionTargetRepository;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
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
    private CacheService cacheService;

    @InjectMocks
    private ScoutingSessionService scoutingSessionService;

    private Farm testFarm;
    private User manager;
    private User scout;
    private Greenhouse greenhouse;
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
                List.of(targetRequest),
                LocalDate.now(),
                1,
                "Tomato",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.of(10, 30),
                "Sunny and warm",
                "Test notes"
        );
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
        when(greenhouseRepository.findById(greenhouse.getId()))
                .thenReturn(Optional.of(greenhouse));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenReturn(testSession);

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.createSession(createRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.farmId()).isEqualTo(testFarm.getId());
        verify(farmAccessService).requireAdminOrSuperAdmin(testFarm);
        verify(sessionRepository).save(any(ScoutingSession.class));
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
    @DisplayName("Should update session successfully")
    void updateSession_WithValidData_UpdatesSession() {
        // Arrange
        UpdateScoutingSessionRequest updateRequest = new UpdateScoutingSessionRequest(
                LocalDate.now().plusDays(1),
                2,
                null,
                "Updated Crop",
                "Updated Variety",
                new BigDecimal("25.0"),
                null,
                null,
                null,
                "Updated notes"
        );

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenReturn(testSession);

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
                "Crop",
                "Variety",
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
    @DisplayName("Should start session successfully")
    void startSession_WithDraftSession_StartsSession() {
        // Arrange
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenReturn(testSession);

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
    @DisplayName("Should throw BadRequestException when starting completed session")
    void startSession_WithCompletedSession_ThrowsBadRequestException() {
        // Arrange
        testSession.setStatus(SessionStatus.COMPLETED);
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        // Act & Assert
        assertThatThrownBy(() -> scoutingSessionService.startSession(testSession.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been completed");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should complete session with acknowledgment")
    void completeSession_WithAcknowledgment_CompletesSession() {
        // Arrange
        testSession.setStatus(SessionStatus.IN_PROGRESS);
        CompleteSessionRequest request = new CompleteSessionRequest(1L, true);

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenReturn(testSession);

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
    @DisplayName("Should throw BadRequestException when completing without acknowledgment")
    void completeSession_WithoutAcknowledgment_ThrowsBadRequestException() {
        // Arrange
        CompleteSessionRequest request = new CompleteSessionRequest(1L, false);

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

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(ScoutingSession.class)))
                .thenReturn(testSession);

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.reopenSession(testSession.getId());

        // Assert
        assertThat(result).isNotNull();
        verify(sessionRepository).save(argThat(session ->
                session.getStatus() == SessionStatus.IN_PROGRESS &&
                        !session.isConfirmationAcknowledged() &&
                        session.getCompletedAt() == null
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
        assertThatThrownBy(() -> scoutingSessionService.reopenSession(testSession.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("completed sessions");

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
                null
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
                1L
        );

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
                null
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
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be edited");

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
        verify(farmAccessService).requireScoutOfFarm(testFarm);
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
                null
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
                SpeciesCode.WHITEFLY,
                1,
                "Bay-1",
                1,
                "Bench-1",
                1,
                4,
                "note",
                null,
                requestId
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
                .speciesCode(SpeciesCode.WHITEFLY)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(3)
                .build();

        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionTargetRepository.findByIdAndSessionId(target.getId(), testSession.getId()))
                .thenReturn(Optional.of(target));
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
                .speciesCode(SpeciesCode.APHIDS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(1)
                .build();
        existing.setVersion(5L);

        UpsertObservationRequest request = new UpsertObservationRequest(
                testSession.getId(),
                target.getId(),
                SpeciesCode.APHIDS,
                1,
                "Bay-1",
                1,
                "Bench-1",
                1,
                2,
                "stale",
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
                .speciesCode(SpeciesCode.WHITEFLY)
                .bayIndex(0)
                .benchIndex(0)
                .spotIndex(0)
                .count(2)
                .build();
        changedObs.setUpdatedAt(LocalDateTime.now());

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
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
        verify(farmAccessService).requireViewAccess(testFarm);
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
                .speciesCode(SpeciesCode.WHITEFLY)
                .bayIndex(0)
                .benchIndex(0)
                .spotIndex(0)
                .count(2)
                .build();
        deletedObs.markDeleted();
        deletedObs.setUpdatedAt(LocalDateTime.now());

        when(farmRepository.findById(testFarm.getId())).thenReturn(Optional.of(testFarm));
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
        when(sessionRepository.findById(testSession.getId()))
                .thenReturn(Optional.of(testSession));

        // Act
        ScoutingSessionDetailDto result = scoutingSessionService.getSession(testSession.getId());

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testSession.getId());
        verify(farmAccessService).requireViewAccess(testFarm);
    }

    @Test
    @DisplayName("Should list sessions for farm")
    void listSessions_WithValidFarm_ReturnsSessions() {
        // Arrange
        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmId(testFarm.getId()))
                .thenReturn(Arrays.asList(testSession));

        // Act
        List<ScoutingSessionDetailDto> result = scoutingSessionService.listSessions(testFarm.getId());

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
        verify(farmAccessService).requireViewAccess(testFarm);
    }
}
