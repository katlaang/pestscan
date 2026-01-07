package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.model.SyncStatus;
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
import mofo.com.pestscout.scouting.model.SessionAuditAction;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionTargetRepository;
import mofo.com.pestscout.scouting.service.SessionAuditService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoutingSessionService {

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final ScoutingSessionTargetRepository sessionTargetRepository;
    private final FarmRepository farmRepository;
    private final FieldBlockRepository fieldBlockRepository;
    private final GreenhouseRepository greenhouseRepository;
    private final CurrentUserService currentUserService;
    private final FarmAccessService farmAccessService;
    private final UserRepository userRepository;
    private final LicenseService licenseService;
    private final CacheService cacheService;
    private final SessionAuditService sessionAuditService;

    /**
     * Create a new scouting session for a farm.
     * The manager assigns a scout and defines basic metadata.
     */
    @Transactional
    public ScoutingSessionDetailDto createSession(CreateScoutingSessionRequest request) {
        Farm farm = farmRepository.findById(request.farmId())
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", request.farmId()));

        farmAccessService.requireAdminOrSuperAdmin(farm);
        licenseService.validateFarmLicenseActive(farm);

        List<ResolvedTarget> resolvedTargets = request.targets().stream()
                .map(target -> resolveTarget(target, farm))
                .toList();

        BigDecimal requestedArea = calculateRequestedArea(resolvedTargets);
        licenseService.validateAreaWithinLicense(farm, requestedArea);

        User manager = resolveManager(farm);
        User scout = resolveScout(request);

        SessionStatus initialStatus = request.status() == null ? SessionStatus.NEW : request.status();
        if (initialStatus != SessionStatus.DRAFT && initialStatus != SessionStatus.NEW) {
            throw new BadRequestException("Session status must be DRAFT or NEW when creating.");
        }

        ScoutingSession session = ScoutingSession.builder()
                .farm(farm)
                .manager(manager)
                .scout(scout)
                .sessionDate(request.sessionDate())
                .weekNumber(resolveWeekNumber(request.sessionDate(), request.weekNumber()))
                .cropType(request.crop())
                .cropVariety(request.variety())
                .temperatureCelsius(request.temperatureCelsius())
                .relativeHumidityPercent(request.relativeHumidityPercent())
                .observationTime(request.observationTime())
                .weatherNotes(request.weatherNotes())
                .notes(request.notes())
                .status(initialStatus)
                .confirmationAcknowledged(false)
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();

        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        resolvedTargets.forEach(target -> session.addTarget(buildTarget(target)));

        ScoutingSession saved = sessionRepository.save(session);
        log.info("Created scouting session {} for farm {}", saved.getId(), farm.getId());
        sessionAuditService.record(saved, SessionAuditAction.SESSION_CREATED, null, null, null, null, null);
        cacheService.evictSessionCachesAfterCommit(farm.getId(), saved.getId());
        return mapToDetailDto(saved);
    }

    /**
     * Update the metadata of a session.
     * Completed sessions must be reopened first.
     */
    @Transactional
    public ScoutingSessionDetailDto updateSession(UUID sessionId, UpdateScoutingSessionRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        farmAccessService.requireAdminOrSuperAdmin(session.getFarm());
        ensureSessionEditableForMetadata(session);

        assertNotStale(request.version(), session.getVersion(), "ScoutingSession");

        if (request.sessionDate() != null) {
            session.setSessionDate(request.sessionDate());
            session.setWeekNumber(resolveWeekNumber(request.sessionDate(), request.weekNumber()));
        }
        if (request.weekNumber() != null) {
            session.setWeekNumber(request.weekNumber());
        }
        if (request.crop() != null) {
            session.setCropType(request.crop());
        }
        if (request.variety() != null) {
            session.setCropVariety(request.variety());
        }
        if (request.temperatureCelsius() != null) {
            session.setTemperatureCelsius(request.temperatureCelsius());
        }
        if (request.relativeHumidityPercent() != null) {
            session.setRelativeHumidityPercent(request.relativeHumidityPercent());
        }
        if (request.observationTime() != null) {
            session.setObservationTime(request.observationTime());
        }
        if (request.weatherNotes() != null) {
            session.setWeatherNotes(request.weatherNotes());
        }
        if (request.notes() != null) {
            session.setNotes(request.notes());
        }

        if (request.targets() != null && !request.targets().isEmpty()) {
            List<ResolvedTarget> resolvedTargets = request.targets().stream()
                    .map(target -> resolveTarget(target, session.getFarm()))
                    .toList();

            session.getTargets().clear();
            resolvedTargets.forEach(target -> session.addTarget(buildTarget(target)));
        }

        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        ScoutingSession saved = sessionRepository.save(session);
        log.info("Updated scouting session {}", saved.getId());
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return mapToDetailDto(saved);
    }

    /**
     * Mark a session as started.
     * This moves the status to IN_PROGRESS and sets startedAt if missing.
     */
    @Transactional
    public ScoutingSessionDetailDto startSession(UUID sessionId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        Role role = farmAccessService.getCurrentUserRole();
        if (role == Role.SCOUT) {
            enforceScoutOwnsSession(session);
        } else {
            farmAccessService.requireAdminOrSuperAdmin(session.getFarm());
        }

        if (isLockedForScout(session)) {
            throw new BadRequestException("Cannot start a session that has already been submitted or completed.");
        }

        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        markOtherInProgressSessionsIncomplete(session);

        ScoutingSession saved = sessionRepository.save(session);
        sessionAuditService.record(saved, SessionAuditAction.SESSION_STARTED, null, null, null, null, null);
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return mapToDetailDto(saved);
    }

    /**
     * Scout submits a session (locks editing for scouts). Works offline on edge.
     */
    @Transactional
    public ScoutingSessionDetailDto submitSession(UUID sessionId, SubmitSessionRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        Role role = farmAccessService.getCurrentUserRole();
        if (role == Role.SCOUT) {
            enforceScoutOwnsSession(session);
        } else {
            farmAccessService.requireViewAccess(session.getFarm());
        }

        if (isLockedForScout(session)) {
            throw new BadRequestException("Session has already been submitted or completed.");
        }

        assertNotStale(request.version(), session.getVersion(), "ScoutingSession");

        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }

        session.markSubmitted(Boolean.TRUE.equals(request.confirmationAcknowledged()));
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        ScoutingSession saved = sessionRepository.save(session);
        sessionAuditService.record(saved, SessionAuditAction.SESSION_SUBMITTED, request.comment(), request.deviceId(), request.deviceType(), request.location(), request.actorName());
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return mapToDetailDto(saved);
    }

    /**
     * Complete a session after the scout confirms the data is correct.
     * Once completed, editing observations is blocked until the session is reopened.
     */
    @Transactional
    public ScoutingSessionDetailDto completeSession(UUID sessionId, CompleteSessionRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        farmAccessService.requireAdminOrSuperAdmin(session.getFarm());

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Session is already completed.");
        }

        if (session.getStatus() != SessionStatus.SUBMITTED && session.getStatus() != SessionStatus.REOPENED) {
            throw new BadRequestException("Session must be submitted before approval.");
        }

        assertNotStale(request.version(), session.getVersion(), "ScoutingSession");

        if (request == null || !Boolean.TRUE.equals(request.confirmationAcknowledged())) {
            throw new BadRequestException("Please confirm all information is correct before completing the session.");
        }

        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        session.markCompleted(true);
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        ScoutingSession saved = sessionRepository.save(session);
        sessionAuditService.record(saved, SessionAuditAction.SESSION_COMPLETED, request.comment(), request.deviceId(), request.deviceType(), request.location(), request.actorName());
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return mapToDetailDto(saved);
    }

    /**
     * Reopen a completed session so that observations can be edited again.
     */
    @Transactional
    public ScoutingSessionDetailDto reopenSession(UUID sessionId, ReopenSessionRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        farmAccessService.requireAdminOrSuperAdmin(session.getFarm());

        if (session.getStatus() != SessionStatus.COMPLETED && session.getStatus() != SessionStatus.SUBMITTED && session.getStatus() != SessionStatus.INCOMPLETE) {
            throw new BadRequestException("Only submitted or completed sessions can be reopened.");
        }

        session.markReopened(request != null ? request.comment() : null);
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        ScoutingSession saved = sessionRepository.save(session);
        sessionAuditService.record(saved, SessionAuditAction.SESSION_REOPENED,
                request != null ? request.comment() : null,
                request != null ? request.deviceId() : null,
                request != null ? request.deviceType() : null,
                request != null ? request.location() : null,
                request != null ? request.actorName() : null);
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return mapToDetailDto(saved);
    }

    /**
     * Create or update a single observation cell (bay, bench, spot, species).
     * If a row exists, its count and notes are updated; otherwise a new row is created.
     */
    @Transactional
    public ScoutingObservationDto upsertObservation(UUID sessionId, UpsertObservationRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        enforceScoutOwnsSession(session);
        ensureScoutCanEdit(session);
        return upsertObservationInternal(session, request);
    }

    @Transactional
    public List<ScoutingObservationDto> bulkUpsertObservations(UUID sessionId, BulkUpsertObservationsRequest request) {
        if (!sessionId.equals(request.sessionId())) {
            throw new BadRequestException("Bulk payload does not match session.");
        }

        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        enforceScoutOwnsSession(session);
        ensureScoutCanEdit(session);
        ensureSessionEditableForObservations(session);

        List<ScoutingObservationDto> observations = request.observations().stream()
                .map(observationRequest -> upsertObservationInternal(session, observationRequest))
                .toList();

        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return observations;
    }

    private ScoutingObservationDto upsertObservationInternal(ScoutingSession session,
                                                             UpsertObservationRequest request) {

        UUID clientRequestId = request.clientRequestId();
        if (clientRequestId != null) {
            Optional<ScoutingObservation> existingByKey =
                    observationRepository.findByClientRequestId(clientRequestId);

            if (existingByKey.isPresent()) {
                ScoutingObservation obs = existingByKey.get();

                if (!obs.getSession().getId().equals(session.getId())) {
                    throw new ConflictException("Idempotency key already used for another session");
                }

                // Same session, idempotent replay
                return mapToObservationDto(obs, false);
            }
        }

        ScoutingSessionTarget target = sessionTargetRepository
                .findByIdAndSessionId(request.sessionTargetId(), session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Session target not found"));

        ensureSessionEditableForObservations(session);
        assertTargetSelectionsAllowCell(target, request.bayTag(), request.benchTag());

        ScoutingObservation observation = observationRepository
                .findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                        session.getId(),
                        target.getId(),
                        request.bayIndex(),
                        request.benchIndex(),
                        request.spotIndex(),
                        request.speciesCode()
                )
                .orElse(null);

        if (observation == null) {
            observation = ScoutingObservation.builder()
                    .session(session)
                    .sessionTarget(target)
                    .speciesCode(request.speciesCode())
                    .bayIndex(request.bayIndex())
                    .bayLabel(request.bayTag())
                    .benchIndex(request.benchIndex())
                    .benchLabel(request.benchTag())
                    .spotIndex(request.spotIndex())
                    .build();
            session.addObservation(observation);
        } else {
            Long requestVersion = request.version();
            Long currentVersion = observation.getVersion();
            if (requestVersion != null && !requestVersion.equals(currentVersion)) {
                throw new ConflictException("Observation has changed on the server");
            }
        }

        observation.setCount(request.count());
        observation.setNotes(request.notes());
        observation.setClientRequestId(clientRequestId);
        observation.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        ScoutingObservation saved = observationRepository.save(observation);
        return mapToObservationDto(saved, false);
    }


    /**
     * Delete a single observation from a session.
     */
    @Transactional
    public void deleteObservation(UUID sessionId, UUID observationId) {
        ScoutingObservation observation = observationRepository
                .findByIdAndSessionId(observationId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingObservation", "id", observationId));

        enforceScoutOwnsSession(observation.getSession());
        ensureScoutCanEdit(observation.getSession());
        ensureSessionEditableForObservations(observation.getSession());
        observation.markDeleted();
        observation.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        observation.getSession().setSyncStatus(SyncStatus.PENDING_UPLOAD);
        observationRepository.save(observation);
        cacheService.evictSessionCachesAfterCommit(observation.getSession().getFarm().getId(), observation.getSession().getId());
    }

    /**
     * Load one session with all its observations and recommendations.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "session-detail",
            key = "#sessionId.toString() + ':tenant=' + #root.target.currentUserService.getCurrentCustomerNumber() + ':user=' + #root.target.currentUserService.getCurrentUserId()",
            unless = "#result == null"
    )
    public ScoutingSessionDetailDto getSession(UUID sessionId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));
        enforceSessionVisibility(session);
        return mapToDetailDto(session);
    }

    /**
     * List all sessions for a farm, newest first.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "sessions-list",
            key = "#farmId.toString() + ':tenant=' + #root.target.currentUserService.getCurrentCustomerNumber() + ':user=' + #root.target.currentUserService.getCurrentUserId()",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<ScoutingSessionDetailDto> listSessions(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
        Role role = farmAccessService.getCurrentUserRole();

        if (role == Role.SCOUT) {
            UUID currentUserId = currentUserService.getCurrentUserId();
            return sessionRepository.findByFarmId(farmId).stream()
                    .filter(session -> session.getScout() != null && currentUserId.equals(session.getScout().getId()))
                    .sorted(Comparator.comparing(ScoutingSession::getSessionDate).reversed())
                    .map(this::mapToDetailDto)
                    .collect(Collectors.toList());
        }

        farmAccessService.requireViewAccess(farm);

        return sessionRepository.findByFarmId(farmId).stream()
                .sorted(Comparator.comparing(ScoutingSession::getSessionDate).reversed())
                .map(this::mapToDetailDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScoutingSyncResponse syncChanges(UUID farmId, LocalDateTime since, boolean includeDeleted) {
        if (since == null) {
            throw new BadRequestException("Parameter 'since' is required for sync.");
        }

        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
        Role role = farmAccessService.getCurrentUserRole();
        UUID currentUserId = currentUserService.getCurrentUserId();

        if (role == Role.SCOUT) {
            List<ScoutingSession> updatedSessions = sessionRepository.findByFarmIdAndUpdatedAtAfter(farmId, since).stream()
                    .filter(session -> session.getScout() != null && currentUserId.equals(session.getScout().getId()))
                    .toList();

            List<UUID> farmSessionIds = sessionRepository.findByFarmId(farmId).stream()
                    .filter(session -> session.getScout() != null && currentUserId.equals(session.getScout().getId()))
                    .map(ScoutingSession::getId)
                    .toList();

            List<ScoutingObservation> changedObservations = farmSessionIds.isEmpty()
                    ? List.of()
                    : observationRepository.findBySessionIdInAndUpdatedAtAfter(farmSessionIds, since);

            Set<UUID> touchedSessionIds = new HashSet<>();
            updatedSessions.forEach(session -> touchedSessionIds.add(session.getId()));
            changedObservations.forEach(observation -> touchedSessionIds.add(observation.getSession().getId()));

            List<ScoutingSessionDetailDto> sessionDtos = touchedSessionIds.isEmpty()
                    ? List.of()
                    : sessionRepository.findAllById(touchedSessionIds).stream()
                    .filter(session -> session.getScout() != null && currentUserId.equals(session.getScout().getId()))
                    .map(session -> mapToDetailDto(session, includeDeleted))
                    .toList();

            List<ScoutingObservationDto> observationDtos = changedObservations.stream()
                    .filter(observation -> includeDeleted || !observation.isDeleted())
                    .map(observation -> mapToObservationDto(observation, includeDeleted))
                    .toList();

            return new ScoutingSyncResponse(sessionDtos, observationDtos);
        }

        farmAccessService.requireViewAccess(farm);

        List<ScoutingSession> updatedSessions = sessionRepository.findByFarmIdAndUpdatedAtAfter(farmId, since);
        List<UUID> farmSessionIds = sessionRepository.findByFarmId(farmId).stream()
                .map(ScoutingSession::getId)
                .toList();

        List<ScoutingObservation> changedObservations = farmSessionIds.isEmpty()
                ? List.of()
                : observationRepository.findBySessionIdInAndUpdatedAtAfter(farmSessionIds, since);

        Set<UUID> touchedSessionIds = new HashSet<>();
        updatedSessions.forEach(session -> touchedSessionIds.add(session.getId()));
        changedObservations.forEach(observation -> touchedSessionIds.add(observation.getSession().getId()));

        List<ScoutingSessionDetailDto> sessionDtos = touchedSessionIds.isEmpty()
                ? List.of()
                : sessionRepository.findAllById(touchedSessionIds).stream()
                .map(session -> mapToDetailDto(session, includeDeleted))
                .toList();

        List<ScoutingObservationDto> observationDtos = changedObservations.stream()
                .filter(observation -> includeDeleted || !observation.isDeleted())
                .map(observation -> mapToObservationDto(observation, includeDeleted))
                .toList();

        return new ScoutingSyncResponse(sessionDtos, observationDtos);
    }

    /**
     * Count how many sessions a farm completed in the current calendar week.
     */
    @Transactional(readOnly = true)
    public long countCompletedSessionsThisWeek(UUID farmId) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        LocalDateTime now = LocalDateTime.now();
        int currentWeek = now.get(weekFields.weekOfWeekBasedYear());
        int currentYear = now.get(weekFields.weekBasedYear());

        return sessionRepository.findByFarmId(farmId).stream()
                .filter(session -> session.getStatus() == SessionStatus.COMPLETED)
                .filter(session -> session.getCompletedAt() != null)
                .filter(session -> {
                    LocalDateTime completedAt = session.getCompletedAt();
                    int sessionWeek = completedAt.get(weekFields.weekOfWeekBasedYear());
                    int sessionYear = completedAt.get(weekFields.weekBasedYear());
                    return sessionWeek == currentWeek && sessionYear == currentYear;
                })
                .count();
    }

    /**
     * Only sessions in DRAFT or IN_PROGRESS can have their metadata changed.
     */
    private void ensureSessionEditableForMetadata(ScoutingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.SUBMITTED
                || session.getStatus() == SessionStatus.INCOMPLETE) {
            throw new BadRequestException("Locked sessions must be reopened before editing.");
        }
    }

    /**
     * Only sessions in DRAFT or IN_PROGRESS can have their observations changed.
     */
    private void ensureSessionEditableForObservations(ScoutingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.CANCELLED
                || session.getStatus() == SessionStatus.SUBMITTED
                || session.getStatus() == SessionStatus.INCOMPLETE) {
            throw new BadRequestException("Locked sessions cannot be edited.");
        }
    }

    private boolean isLockedForScout(ScoutingSession session) {
        return session.getStatus() == SessionStatus.SUBMITTED
                || session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.INCOMPLETE
                || session.getStatus() == SessionStatus.CANCELLED;
    }

    private void ensureScoutCanEdit(ScoutingSession session) {
        if (farmAccessService.getCurrentUserRole() != Role.SCOUT) {
            return;
        }

        if (isLockedForScout(session)) {
            throw new ForbiddenException("Session is locked and cannot be edited by scout.");
        }
    }

    private void markOtherInProgressSessionsIncomplete(ScoutingSession session) {
        if (session.getScout() == null || session.getFarm() == null) {
            return;
        }

        List<ScoutingSession> overlapping = sessionRepository.findByFarmIdAndScoutIdAndStatus(
                session.getFarm().getId(), session.getScout().getId(), SessionStatus.IN_PROGRESS);

        overlapping.stream()
                .filter(other -> !other.getId().equals(session.getId()))
                .forEach(other -> {
                    other.markIncomplete();
                    other.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                    sessionAuditService.record(other, SessionAuditAction.SESSION_MARKED_INCOMPLETE,
                            "New session started while another was open", null, null, null, null);
                    sessionRepository.save(other);
                });
    }

    private void assertNotStale(Long requestedVersion, Long currentVersion, String entityName) {
        if (requestedVersion == null) {
            return;
        }
        if (!Objects.equals(requestedVersion, currentVersion)) {
            throw new ConflictException(entityName + " has changed on the server. Please sync and retry.");
        }
    }

    private ScoutingObservation resolveObservationForUpsert(ScoutingSession session,
                                                            ScoutingSessionTarget target,
                                                            UpsertObservationRequest request) {
        if (request.clientRequestId() != null) {
            Optional<ScoutingObservation> byRequestId = observationRepository.findByClientRequestId(request.clientRequestId());
            if (byRequestId.isPresent() && !byRequestId.get().getSession().getId().equals(session.getId())) {
                throw new ConflictException("Idempotency key already used for another session.");
            }
            if (byRequestId.isPresent()) {
                return byRequestId.get();
            }
        }

        Optional<ScoutingObservation> existingOpt = observationRepository
                .findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                        session.getId(),
                        target.getId(),
                        request.bayIndex(),
                        request.benchIndex(),
                        request.spotIndex(),
                        request.speciesCode()
                );

        return existingOpt.orElseGet(() -> {
            ScoutingObservation created = ScoutingObservation.builder()
                    .session(session)
                    .sessionTarget(target)
                    .speciesCode(request.speciesCode())
                    .bayIndex(request.bayIndex())
                    .bayLabel(request.bayTag())
                    .benchIndex(request.benchIndex())
                    .benchLabel(request.benchTag())
                    .spotIndex(request.spotIndex())
                    .clientRequestId(request.clientRequestId())
                    .build();
            session.addObservation(created);
            return created;
        });
    }

    private User resolveManager(Farm farm) {
        if (farmAccessService.isSuperAdmin()) {
            return farm.getOwner();
        }
        return currentUserService.getCurrentUser();
    }

    private User resolveScout(CreateScoutingSessionRequest request) {
        User scout = userRepository.findById(request.scoutId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.scoutId()));

        if (scout.getRole() != Role.SCOUT) {
            throw new BadRequestException("Assigned user must be a scout.");
        }

        if (!scout.isActive()) {
            throw new BadRequestException("Assigned scout is inactive or deleted.");
        }

        return scout;
    }

    private void enforceScoutOwnsSession(ScoutingSession session) {
        if (farmAccessService.getCurrentUserRole() != Role.SCOUT) {
            return;
        }

        UUID currentUserId = currentUserService.getCurrentUserId();
        if (session.getScout() != null && currentUserId.equals(session.getScout().getId())) {
            return;
        }

        throw new ForbiddenException("You are not assigned to this scouting session.");
    }

    private void enforceSessionVisibility(ScoutingSession session) {
        Role role = farmAccessService.getCurrentUserRole();
        if (role == Role.SCOUT) {
            enforceScoutOwnsSession(session);
            return;
        }

        farmAccessService.requireViewAccess(session.getFarm());
    }

    /**
     * Copy recommendations into a fresh EnumMap to avoid exposing internal maps.
     */
    private Map<RecommendationType, String> copyRecommendations(Map<RecommendationType, String> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return new EnumMap<>(RecommendationType.class);
        }
        return new EnumMap<>(recommendations);
    }

    /**
     * Convert a session entity into a detailed DTO including observations and recommendations.
     */
    private ScoutingSessionDetailDto mapToDetailDto(ScoutingSession session) {
        return mapToDetailDto(session, false);
    }

    private ScoutingSessionDetailDto mapToDetailDto(ScoutingSession session, boolean includeDeletedObservations) {
        Map<UUID, List<ScoutingObservation>> observationsByTarget = session.getObservations().stream()
                .filter(observation -> includeDeletedObservations || !observation.isDeleted())
                .collect(Collectors.groupingBy(observation -> observation.getSessionTarget().getId()));

        List<ScoutingSessionSectionDto> sectionDtos = session.getTargets().stream()
                .sorted(Comparator.comparing(target -> {
                    if (target.getGreenhouse() != null) {
                        return target.getGreenhouse().getName();
                    }
                    return target.getFieldBlock() != null ? target.getFieldBlock().getName() : "";
                }, String.CASE_INSENSITIVE_ORDER))
                .map(target -> {
                    List<ScoutingObservationDto> targetObservations = observationsByTarget
                            .getOrDefault(target.getId(), List.of())
                            .stream()
                            .sorted(Comparator
                                    .comparing(ScoutingObservation::getBayIndex, Comparator.nullsLast(Integer::compareTo))
                                    .thenComparing(ScoutingObservation::getBenchIndex, Comparator.nullsLast(Integer::compareTo))
                                    .thenComparing(ScoutingObservation::getSpotIndex, Comparator.nullsLast(Integer::compareTo)))
                            .map(observation -> mapToObservationDto(observation, includeDeletedObservations))
                            .toList();

                    UUID greenhouseId = target.getGreenhouse() != null ? target.getGreenhouse().getId() : null;
                    UUID fieldBlockId = target.getFieldBlock() != null ? target.getFieldBlock().getId() : null;

                    return new ScoutingSessionSectionDto(
                            target.getId(),
                            greenhouseId,
                            fieldBlockId,
                            target.getIncludeAllBays(),
                            target.getIncludeAllBenches(),
                            List.copyOf(target.getBayTags()),
                            List.copyOf(target.getBenchTags()),
                            targetObservations
                    );
                })
                .toList();

        List<RecommendationEntryDto> recommendationDtos = session.getRecommendations().entrySet().stream()
                .map(entry -> new RecommendationEntryDto(entry.getKey(), entry.getValue()))
                .toList();

        UUID managerId = session.getManager() != null ? session.getManager().getId() : null;
        UUID scoutId = session.getScout() != null ? session.getScout().getId() : null;

        return new ScoutingSessionDetailDto(
                session.getId(),
                session.getVersion(),
                session.getFarm().getId(),
                session.getSessionDate(),
                session.getWeekNumber(),
                session.getStatus(),
                session.getSyncStatus(),
                managerId,
                scoutId,
                session.getCropType(),
                session.getCropVariety(),
                session.getTemperatureCelsius(),
                session.getRelativeHumidityPercent(),
                session.getObservationTime(),
                session.getWeatherNotes(),
                session.getNotes(),
                session.getStartedAt(),
                session.getSubmittedAt(),
                session.getCompletedAt(),
                session.getUpdatedAt(),
                session.isConfirmationAcknowledged(),
                session.getReopenComment(),
                sectionDtos,
                recommendationDtos
        );
    }

    /**
     * Convert an observation entity into a DTO for the API.
     */
    private ScoutingObservationDto mapToObservationDto(ScoutingObservation observation, boolean includeDeleted) {
        if (observation == null) {
            throw new IllegalArgumentException("Observation must not be null");
        }
        ObservationCategory category = observation.getCategory(); // derived from speciesCode in the entity

        boolean deleted = includeDeleted && observation.isDeleted();

        return new ScoutingObservationDto(
                observation.getId(),
                observation.getVersion(),
                observation.getSession().getId(),
                observation.getSessionTarget().getId(),
                observation.getSessionTarget().getGreenhouse() != null ? observation.getSessionTarget().getGreenhouse().getId() : null,
                observation.getSessionTarget().getFieldBlock() != null ? observation.getSessionTarget().getFieldBlock().getId() : null,
                observation.getSpeciesCode(),
                category,
                observation.getBayIndex(),
                observation.getBayLabel(),
                observation.getBenchIndex(),
                observation.getBenchLabel(),
                observation.getSpotIndex(),
                observation.getCount() != null ? observation.getCount() : 0,
                observation.getNotes(),
                observation.getUpdatedAt(),
                observation.getSyncStatus(),
                deleted,
                observation.getDeletedAt(),
                observation.getClientRequestId()
        );
    }

    private ScoutingSessionTarget buildTarget(ResolvedTarget target) {
        return ScoutingSessionTarget.builder()
                .greenhouse(target.greenhouse())
                .fieldBlock(target.fieldBlock())
                .includeAllBays(target.includeAllBays())
                .includeAllBenches(target.includeAllBenches())
                .bayTags(target.bayTags())
                .benchTags(target.benchTags())
                .build();
    }

    private ResolvedTarget resolveTarget(SessionTargetRequest targetRequest, Farm farm) {
        UUID greenhouseId = targetRequest.greenhouseId();
        UUID fieldBlockId = targetRequest.fieldBlockId();
        Greenhouse greenhouse = loadGreenhouse(greenhouseId, farm.getId());
        FieldBlock fieldBlock = loadFieldBlock(fieldBlockId, farm.getId());

        boolean includeAllBays = targetRequest.includeAllBays() == null || targetRequest.includeAllBays();
        boolean includeAllBenches = targetRequest.includeAllBenches() == null || targetRequest.includeAllBenches();

        List<String> bayTags = normalizeTags(targetRequest.bayTags());
        List<String> benchTags = normalizeTags(targetRequest.benchTags());

        if (!includeAllBays && bayTags.isEmpty()) {
            throw new BadRequestException("Provide bayTags when includeAllBays is false.");
        }
        if (!includeAllBenches && benchTags.isEmpty()) {
            throw new BadRequestException("Provide benchTags when includeAllBenches is false.");
        }

        return new ResolvedTarget(greenhouse, fieldBlock, includeAllBays, includeAllBenches, bayTags, benchTags);
    }

    private BigDecimal calculateRequestedArea(List<ResolvedTarget> resolvedTargets) {
        return resolvedTargets.stream()
                .map(this::calculateTargetArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTargetArea(ResolvedTarget target) {
        int bayCount = target.greenhouse() != null
                ? target.greenhouse().resolvedBayCount()
                : target.fieldBlock().resolvedBayCount();
        int selectedBays = Boolean.TRUE.equals(target.includeAllBays()) ? bayCount : target.bayTags().size();
        return BigDecimal.valueOf(selectedBays);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private void assertTargetSelectionsAllowCell(ScoutingSessionTarget target, String bayTag, String benchTag) {
        if (!Boolean.TRUE.equals(target.getIncludeAllBays()) && bayTag != null && !target.getBayTags().contains(bayTag)) {
            throw new BadRequestException("Selected bay is not part of this session target.");
        }
        if (!Boolean.TRUE.equals(target.getIncludeAllBenches()) && benchTag != null && !target.getBenchTags().contains(benchTag)) {
            throw new BadRequestException("Selected bench is not part of this session target.");
        }
    }

    private Greenhouse loadGreenhouse(UUID greenhouseId, UUID farmId) {
        if (greenhouseId == null) {
            return null;
        }
        return greenhouseRepository.findById(greenhouseId)
                .filter(gh -> gh.getFarm().getId().equals(farmId))
                .orElseThrow(() -> new BadRequestException("Greenhouse does not belong to this farm."));
    }

    private FieldBlock loadFieldBlock(UUID fieldBlockId, UUID farmId) {
        if (fieldBlockId == null) {
            return null;
        }
        return fieldBlockRepository.findById(fieldBlockId)
                .filter(block -> block.getFarm().getId().equals(farmId))
                .orElseThrow(() -> new BadRequestException("Field block does not belong to this farm."));
    }

    private record ResolvedTarget(
            Greenhouse greenhouse,
            FieldBlock fieldBlock,
            Boolean includeAllBays,
            Boolean includeAllBenches,
            List<String> bayTags,
            List<String> benchTags
    ) {
    }

    private int resolveWeekNumber(java.time.LocalDate date, Integer requestedWeek) {
        if (requestedWeek != null) {
            return requestedWeek;
        }
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return date.get(weekFields.weekOfWeekBasedYear());
    }
}
