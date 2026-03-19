package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
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
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.*;
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

    private static final UUID UNASSIGNED_USER_ID = new UUID(0L, 0L);

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final ScoutingObservationDraftRepository observationDraftRepository;
    private final ScoutingSessionTargetRepository sessionTargetRepository;
    private final SessionAuditEventRepository auditEventRepository;
    private final FarmRepository farmRepository;
    private final FieldBlockRepository fieldBlockRepository;
    private final GreenhouseRepository greenhouseRepository;
    private final CurrentUserService currentUserService;
    private final FarmAccessService farmAccessService;
    private final UserRepository userRepository;
    private final UserFarmMembershipRepository membershipRepository;
    private final LicenseService licenseService;
    private final CacheService cacheService;
    private final SessionAuditService sessionAuditService;
    private final CustomSpeciesDefinitionRepository customSpeciesDefinitionRepository;

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

        List<ResolvedTarget> resolvedTargets = resolveTargets(request, farm);

        BigDecimal requestedArea = calculateRequestedArea(resolvedTargets);
        licenseService.validateAreaWithinLicense(farm, requestedArea);

        User manager = resolveManager(farm);
        User scout = resolveScout(request, farm);
        List<CustomSpeciesDefinition> customSurveySpecies = resolveCustomSurveySpecies(farm, request.customSurveySpeciesIds());

        SessionStatus initialStatus = request.status() == null ? SessionStatus.DRAFT : request.status();
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
                .surveySpecies(normalizeSurveySpecies(request.surveySpeciesCodes()))
                .customSurveySpecies(customSurveySpecies)
                .defaultPhotoSourceType(
                        request.defaultPhotoSourceType() != null
                                ? request.defaultPhotoSourceType()
                                : PhotoSourceType.SCOUT_HANDHELD
                )
                .status(initialStatus)
                .confirmationAcknowledged(false)
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();

        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        resolvedTargets.forEach(target -> session.addTarget(buildTarget(target)));
        if (request.status() == SessionStatus.NEW && !isPlanningComplete(session)) {
            throw new BadRequestException("A scouting session needs an assigned scout and at least one target before it can move to NEW.");
        }
        normalizePlanningStatus(session);

        ScoutingSession saved = sessionRepository.save(session);
        log.info("Created scouting session {} for farm {}", saved.getId(), farm.getId());
        sessionAuditService.record(saved, SessionAuditAction.SESSION_CREATED, request.comment(),
                request.deviceId(), request.deviceType(), request.location(), request.actorName());
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

        if (farmAccessService.getCurrentUserRole() == Role.SCOUT) {
            return updateSessionAsScout(session, request);
        }

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
        if (request.scoutId() != null) {
            session.setScout(resolveRequestedScout(request.scoutId(), session.getFarm()));
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
        if (request.surveySpeciesCodes() != null) {
            session.setSurveySpecies(normalizeSurveySpecies(request.surveySpeciesCodes()));
        }
        if (request.customSurveySpeciesIds() != null) {
            session.setCustomSurveySpecies(resolveCustomSurveySpecies(session.getFarm(), request.customSurveySpeciesIds()));
        }
        if (request.defaultPhotoSourceType() != null) {
            session.setDefaultPhotoSourceType(request.defaultPhotoSourceType());
        }

        if (request.status() != null && request.status() != session.getStatus()) {
            if (request.status() == SessionStatus.DRAFT || request.status() == SessionStatus.NEW) {
                if (session.getStatus() != SessionStatus.DRAFT && session.getStatus() != SessionStatus.NEW) {
                    throw new BadRequestException("Only planning sessions can move between DRAFT and NEW during metadata edits.");
                }
            } else {
                SessionStateMachine.assertTransition(session.getStatus(), request.status(), farmAccessService.getCurrentUserRole());
                session.setStatus(request.status());
            }
        }

        if (request.targets() != null && !request.targets().isEmpty()) {
            List<ResolvedTarget> resolvedTargets = request.targets().stream()
                    .map(target -> resolveTarget(target, session.getFarm()))
                    .toList();

            session.getTargets().clear();
            resolvedTargets.forEach(target -> session.addTarget(buildTarget(target)));
        }

        if (request.status() == SessionStatus.NEW && !isPlanningComplete(session)) {
            throw new BadRequestException("A scouting session needs an assigned scout and at least one target before it can move to NEW.");
        }
        normalizePlanningStatus(session);
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        ScoutingSession saved = sessionRepository.save(session);
        log.info("Updated scouting session {}", saved.getId());
        sessionAuditService.record(saved, SessionAuditAction.SESSION_EDITED, request.comment(),
                request.deviceId(), request.deviceType(), request.location(), request.actorName());
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return mapToDetailDto(saved);
    }

    private ScoutingSessionDetailDto updateSessionAsScout(ScoutingSession session, UpdateScoutingSessionRequest request) {
        enforceSessionVisibility(session);
        enforceScoutOwnsSession(session);
        ensureScoutCanEdit(session);
        assertScoutOnlyRuntimeChanges(request);
        assertNotStale(request.version(), session.getVersion(), "ScoutingSession");

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

        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        ScoutingSession saved = sessionRepository.save(session);
        log.info("Updated scouting session {} runtime fields as scout", saved.getId());
        sessionAuditService.record(saved, SessionAuditAction.SESSION_EDITED, request.comment(),
                request.deviceId(), request.deviceType(), request.location(), request.actorName());
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), session.getId());
        return mapToDetailDto(saved);
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        farmAccessService.requireAdminOrSuperAdmin(session.getFarm());

        if (session.getStatus() != SessionStatus.DRAFT && session.getStatus() != SessionStatus.NEW) {
            throw new BadRequestException("Only draft or new scouting sessions can be deleted.");
        }

        sessionRepository.delete(session);
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
    }

    @Transactional
    public ScoutingSessionDetailDto reuseSession(UUID sessionId) {
        ScoutingSession sourceSession = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        Farm farm = sourceSession.getFarm();
        farmAccessService.requireAdminOrSuperAdmin(farm);
        licenseService.validateFarmLicenseActive(farm);

        List<ResolvedTarget> resolvedTargets = resolveTargets(sourceSession);
        BigDecimal requestedArea = calculateRequestedArea(resolvedTargets);
        licenseService.validateAreaWithinLicense(farm, requestedArea);

        ScoutingSession reusedSession = ScoutingSession.builder()
                .farm(farm)
                .manager(resolveManager(farm))
                .scout(resolveReusableScout(sourceSession))
                .sessionDate(sourceSession.getSessionDate())
                .weekNumber(resolveWeekNumber(sourceSession.getSessionDate(), sourceSession.getWeekNumber()))
                .cropType(sourceSession.getCropType())
                .cropVariety(sourceSession.getCropVariety())
                .notes(sourceSession.getNotes())
                .surveySpecies(new ArrayList<>(normalizeSurveySpecies(sourceSession.getSurveySpecies())))
                .customSurveySpecies(new ArrayList<>(sourceSession.getCustomSurveySpecies()))
                .defaultPhotoSourceType(sourceSession.getDefaultPhotoSourceType())
                .status(SessionStatus.DRAFT)
                .confirmationAcknowledged(false)
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();

        reusedSession.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        resolvedTargets.forEach(target -> reusedSession.addTarget(buildTarget(target)));

        ScoutingSession saved = sessionRepository.save(reusedSession);
        log.info("Reused scouting session {} into draft {}", sourceSession.getId(), saved.getId());
        sessionAuditService.record(saved, SessionAuditAction.SESSION_REUSED, null, null, null, null, null);
        cacheService.evictSessionCachesAfterCommit(farm.getId(), saved.getId());
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
        if (role != Role.SCOUT) {
            throw new ForbiddenException("Only the assigned scout can start a scouting session directly.");
        }

        enforceScoutOwnsSession(session);
        return startSessionInternal(session, null, null, null, null, null);
    }

    @Transactional
    public ScoutingSessionDetailDto requestRemoteStart(UUID sessionId, RemoteStartSessionRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        Role role = farmAccessService.getCurrentUserRole();
        if (role != Role.SUPER_ADMIN) {
            throw new ForbiddenException("Only a super admin can request a remote session start.");
        }

        if (session.getScout() == null) {
            throw new BadRequestException("Remote start requires an assigned scout.");
        }

        assertNotStale(request.version(), session.getVersion(), "ScoutingSession");
        validateSessionCanBeStartedByScout(session);

        User actor = currentUserService.getCurrentUser();
        session.requestRemoteStart(actor.getId(), resolveActorName(request.actorName(), actor));
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        ScoutingSession saved = sessionRepository.save(session);
        sessionAuditService.record(saved, SessionAuditAction.SESSION_REMOTE_START_REQUESTED, request.comment(),
                request.deviceId(), request.deviceType(), request.location(), request.actorName());
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return mapToDetailDto(saved);
    }

    @Transactional
    public ScoutingSessionDetailDto acceptRemoteStart(UUID sessionId, AcceptRemoteStartRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        Role role = farmAccessService.getCurrentUserRole();
        if (role != Role.SCOUT) {
            throw new ForbiddenException("Only the assigned scout can accept a remote session start.");
        }

        enforceScoutOwnsSession(session);
        assertNotStale(request.version(), session.getVersion(), "ScoutingSession");

        if (!session.isRemoteStartPending()) {
            throw new BadRequestException("There is no pending remote start request for this session.");
        }

        return startSessionInternal(session, request.comment(), request.deviceId(), request.deviceType(),
                request.location(), request.actorName());
    }

    /**
     * Scout submits a session (locks editing for scouts). Works offline on edge.
     */
    @Transactional
    public ScoutingSessionDetailDto submitSession(UUID sessionId, SubmitSessionRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        Role role = farmAccessService.getCurrentUserRole();
        if (role != Role.SCOUT) {
            throw new ForbiddenException("Only the assigned scout can submit a scouting session.");
        }

        enforceScoutOwnsSession(session);

        if (isLockedForScout(session)) {
            throw new BadRequestException("Session has already been submitted or completed.");
        }

        assertNotStale(request.version(), session.getVersion(), "ScoutingSession");

        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }

        SessionStateMachine.assertTransition(session.getStatus(), SessionStatus.SUBMITTED, role);
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

        Role role = farmAccessService.getCurrentUserRole();
        if (role != Role.SCOUT) {
            throw new ForbiddenException("Only the assigned scout can complete a scouting session.");
        }

        enforceScoutOwnsSession(session);

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Session is already completed.");
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS
                && session.getStatus() != SessionStatus.SUBMITTED
                && session.getStatus() != SessionStatus.REOPENED
                && session.getStatus() != SessionStatus.INCOMPLETE) {
            throw new BadRequestException("Scout can only complete an in-progress, submitted, reopened, or incomplete session.");
        }

        assertNotStale(request.version(), session.getVersion(), "ScoutingSession");

        if (request == null || !Boolean.TRUE.equals(request.confirmationAcknowledged())) {
            throw new BadRequestException("Please confirm all information is correct before completing the session.");
        }

        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        promoteDraftObservationsToCommitted(session);
        SessionStateMachine.assertTransition(session.getStatus(), SessionStatus.COMPLETED, role);
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

        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BadRequestException("Only completed sessions can be reopened.");
        }

        SessionStateMachine.assertTransition(session.getStatus(), SessionStatus.REOPENED, farmAccessService.getCurrentUserRole());
        session.markReopened(request != null ? request.comment() : null);
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        seedDraftObservationsFromCommitted(session);

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

        requireScoutRole("Only the assigned scout can record scouting observations.");
        enforceScoutOwnsSession(session);
        ensureScoutCanEdit(session);
        ScoutingObservationDto observation = upsertObservationInternal(session, request);
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return observation;
    }

    @Transactional
    public ScoutingObservationDto updateObservation(UUID sessionId, UUID observationId, UpsertObservationRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        requireScoutRole("Only the assigned scout can record scouting observations.");
        enforceScoutOwnsSession(session);
        ensureScoutCanEdit(session);
        ensureSessionEditableForObservations(session);
        ensureDraftWorkspaceInitialized(session);

        ScoutingObservationDraft observation = observationDraftRepository.findByIdAndSessionId(observationId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingObservationDraft", "id", observationId));

        UUID clientRequestId = request.clientRequestId();
        if (clientRequestId != null) {
            Optional<ScoutingObservationDraft> existingByKey = observationDraftRepository.findByClientRequestId(clientRequestId);
            if (existingByKey.isPresent() && !Objects.equals(existingByKey.get().getId(), observationId)) {
                throw new ConflictException("Idempotency key already used for another observation");
            }
        }

        if (observation.isDeleted()) {
            observation.restore();
        } else {
            assertNotStale(request.version(), observation.getVersion(), "Observation");
        }

        ScoutingSessionTarget target = request.sessionTargetId() != null
                ? resolveObservationTarget(session, request)
                : observation.getSessionTarget();
        int spotIndex = request.spotIndex() != null
                ? request.spotIndex()
                : Optional.ofNullable(observation.getSpotIndex()).orElse(1);
        ResolvedObservationSpecies resolvedSpecies = resolveObservationSpecies(session, request, observation);
        String bayTag = request.bayTag() != null ? request.bayTag() : observation.getBayLabel();
        String benchTag = request.benchTag() != null ? request.benchTag() : observation.getBenchLabel();

        assertTargetSelectionsAllowCell(target, bayTag, benchTag);
        assertSpeciesAllowed(session, resolvedSpecies);

        ScoutingObservationDraft conflictingObservation = observationDraftRepository
                .findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesIdentifier(
                        session.getId(),
                        target.getId(),
                        request.bayIndex(),
                        request.benchIndex(),
                        spotIndex,
                        resolvedSpecies.identifier()
                )
                .orElse(null);

        ScoutingObservationDraft observationToSave = observation;
        if (conflictingObservation != null && !Objects.equals(conflictingObservation.getId(), observation.getId())) {
            if (conflictingObservation.isDeleted()) {
                conflictingObservation.restore();
            }
            observation.markDeleted();
            observation.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            observationDraftRepository.save(observation);
            observationToSave = conflictingObservation;
        }

        applyObservationDraftValues(
                observationToSave,
                session,
                target,
                resolvedSpecies,
                request.bayIndex(),
                bayTag,
                request.benchIndex(),
                benchTag,
                spotIndex,
                request.count(),
                request.notes(),
                clientRequestId
        );

        ScoutingObservationDraft saved = observationDraftRepository.save(observationToSave);
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), sessionId);
        return mapToObservationDto(saved);
    }

    @Transactional
    public List<ScoutingObservationDto> bulkUpsertObservations(UUID sessionId, BulkUpsertObservationsRequest request) {
        if (request.sessionId() != null && !sessionId.equals(request.sessionId())) {
            throw new BadRequestException("Bulk payload does not match session.");
        }

        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        requireScoutRole("Only the assigned scout can record scouting observations.");
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
        assertObservationSessionMatches(session.getId(), request);
        ensureDraftWorkspaceInitialized(session);

        UUID clientRequestId = request.clientRequestId();
        if (clientRequestId != null) {
            Optional<ScoutingObservationDraft> existingByKey =
                    observationDraftRepository.findByClientRequestId(clientRequestId);

            if (existingByKey.isPresent()) {
                ScoutingObservationDraft obs = existingByKey.get();

                if (!obs.getSession().getId().equals(session.getId())) {
                    throw new ConflictException("Idempotency key already used for another session");
                }

                // Same session, idempotent replay
                return mapToObservationDto(obs);
            }
        }

        ScoutingSessionTarget target = resolveObservationTarget(session, request);
        int spotIndex = resolveSpotIndex(request);

        ensureSessionEditableForObservations(session);
        assertTargetSelectionsAllowCell(target, request.bayTag(), request.benchTag());
        ResolvedObservationSpecies resolvedSpecies = resolveObservationSpecies(session, request);
        assertSpeciesAllowed(session, resolvedSpecies);

        ScoutingObservationDraft observation = observationDraftRepository
                .findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesIdentifier(
                        session.getId(),
                        target.getId(),
                        request.bayIndex(),
                        request.benchIndex(),
                        spotIndex,
                        resolvedSpecies.identifier()
                )
                .orElse(null);

        if (observation == null) {
            observation = ScoutingObservationDraft.builder()
                    .session(session)
                    .sessionTarget(target)
                    .speciesCode(resolvedSpecies.speciesCode())
                    .customSpecies(resolvedSpecies.customSpecies())
                    .speciesIdentifier(resolvedSpecies.identifier())
                    .bayIndex(request.bayIndex())
                    .bayLabel(request.bayTag())
                    .benchIndex(request.benchIndex())
                    .benchLabel(request.benchTag())
                    .spotIndex(spotIndex)
                    .build();
        } else {
            if (observation.isDeleted()) {
                observation.restore();
            } else {
                Long requestVersion = request.version();
                Long currentVersion = observation.getVersion();
                if (requestVersion != null && !requestVersion.equals(currentVersion)) {
                    throw new ConflictException("Observation has changed on the server");
                }
            }
        }

        applyObservationDraftValues(
                observation,
                session,
                target,
                resolvedSpecies,
                request.bayIndex(),
                request.bayTag(),
                request.benchIndex(),
                request.benchTag(),
                spotIndex,
                request.count(),
                request.notes(),
                clientRequestId
        );

        ScoutingObservationDraft saved = observationDraftRepository.save(observation);
        return mapToObservationDto(saved);
    }


    /**
     * Delete a single observation from a session.
     */
    @Transactional
    public void deleteObservation(UUID sessionId, UUID observationId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        requireScoutRole("Only the assigned scout can record scouting observations.");
        enforceScoutOwnsSession(session);
        ensureScoutCanEdit(session);
        ensureSessionEditableForObservations(session);
        ensureDraftWorkspaceInitialized(session);

        ScoutingObservationDraft observation = observationDraftRepository
                .findByIdAndSessionId(observationId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingObservationDraft", "id", observationId));

        observation.markDeleted();
        observation.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        observationDraftRepository.save(observation);
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), session.getId());
    }

    /**
     * Load one session with all its observations and recommendations.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "session-detail",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null"
    )
    public ScoutingSessionDetailDto getSession(UUID sessionId) {
        return getSessionInternal(sessionId, null, null, null, null, false);
    }

    @Transactional(readOnly = true)
    public ScoutingSessionDetailDto getSession(UUID sessionId, String deviceId, String deviceType, String location, String actorName) {
        return getSessionInternal(sessionId, deviceId, deviceType, location, actorName, true);
    }

    private ScoutingSessionDetailDto getSessionInternal(UUID sessionId,
                                                        String deviceId,
                                                        String deviceType,
                                                        String location,
                                                        String actorName,
                                                        boolean recordAudit) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));
        enforceSessionOpenAccess(session);
        if (recordAudit) {
            sessionAuditService.record(session, SessionAuditAction.SESSION_VIEWED, null, deviceId, deviceType, location, actorName);
        }
        return mapToDetailDto(session);
    }

    /**
     * List all sessions for a farm, newest first.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "sessions-list",
            keyGenerator = "tenantAwareKeyGenerator",
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
                    .filter(this::isVisibleToScout)
                    .sorted(Comparator.comparing(ScoutingSession::getSessionDate).reversed())
                    .map(this::mapToViewerDetailDto)
                    .collect(Collectors.toList());
        }

        requireSessionViewerAccess(farm);

        return sessionRepository.findByFarmId(farmId).stream()
                .filter(session -> role != Role.SUPER_ADMIN || isVisibleToSuperAdmin(session))
                .sorted(Comparator.comparing(ScoutingSession::getSessionDate).reversed())
                .map(this::mapToViewerDetailDto)
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

            List<ScoutingObservationDraft> changedObservations = farmSessionIds.isEmpty()
                    ? List.of()
                    : observationDraftRepository.findBySessionIdInAndUpdatedAtAfter(farmSessionIds, since);

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

        requireSessionViewerAccess(farm);

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
                .filter(session -> role != Role.SUPER_ADMIN || isVisibleToSuperAdmin(session))
                .map(session -> mapToViewerDetailDto(session, includeDeleted))
                .toList();

        Set<UUID> restrictedSessionIds = changedObservations.stream()
                .map(ScoutingObservation::getSession)
                .filter(this::isRestrictedInProgressForFarmViewer)
                .map(ScoutingSession::getId)
                .collect(Collectors.toSet());

        List<ScoutingObservationDto> observationDtos = changedObservations.stream()
                .filter(observation -> includeDeleted || !observation.isDeleted())
                .filter(observation -> !restrictedSessionIds.contains(observation.getSession().getId()))
                .map(observation -> mapToObservationDto(observation, includeDeleted))
                .toList();

        return new ScoutingSyncResponse(sessionDtos, observationDtos);
    }

    @Transactional(readOnly = true)
    public List<ScoutingSessionAuditDto> listAuditTrail(UUID sessionId) {
        farmAccessService.requireSuperAdmin();

        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        return auditEventRepository.findBySessionIdOrderByOccurredAtAsc(sessionId).stream()
                .map(event -> new ScoutingSessionAuditDto(
                        event.getId(),
                        sessionId,
                        event.getAction(),
                        event.getActorId(),
                        event.getActorName(),
                        event.getActorEmail(),
                        event.getActorRole(),
                        event.getDeviceId(),
                        event.getDeviceType(),
                        event.getLocation(),
                        event.getComment(),
                        event.getOccurredAt(),
                        event.getSyncStatus()
                ))
                .toList();
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
     * Planning and reopened sessions can still have metadata changed.
     */
    private void ensureSessionEditableForMetadata(ScoutingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.SUBMITTED
                || session.getStatus() == SessionStatus.INCOMPLETE) {
            throw new BadRequestException("Locked sessions must be reopened before editing.");
        }
    }

    private void assertScoutOnlyRuntimeChanges(UpdateScoutingSessionRequest request) {
        if (request.sessionDate() != null
                || request.weekNumber() != null
                || request.targets() != null
                || request.scoutId() != null
                || request.crop() != null
                || request.variety() != null
                || request.surveySpeciesCodes() != null
                || request.customSurveySpeciesIds() != null
                || request.defaultPhotoSourceType() != null
                || request.status() != null) {
            throw new BadRequestException("Scouts can only update weather, observation time, and session notes.");
        }
    }

    /**
     * Only sessions in DRAFT or IN_PROGRESS can have their observations changed.
     */
    private void ensureSessionEditableForObservations(ScoutingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.CANCELLED
                || session.getStatus() == SessionStatus.SUBMITTED) {
            throw new BadRequestException("Locked sessions cannot be edited.");
        }
    }

    private boolean isLockedForScout(ScoutingSession session) {
        return session.getStatus() == SessionStatus.SUBMITTED
                || session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.CANCELLED;
    }

    private ScoutingSessionDetailDto startSessionInternal(ScoutingSession session,
                                                          String comment,
                                                          String deviceId,
                                                          String deviceType,
                                                          String location,
                                                          String actorName) {
        validateSessionCanBeStartedByScout(session);

        session.markStarted();
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        markOtherInProgressSessionsIncomplete(session);

        ScoutingSession saved = sessionRepository.save(session);
        sessionAuditService.record(saved, SessionAuditAction.SESSION_STARTED, comment, deviceId, deviceType, location, actorName);
        cacheService.evictSessionCachesAfterCommit(session.getFarm().getId(), session.getId());
        return mapToDetailDto(saved);
    }

    private void validateSessionCanBeStartedByScout(ScoutingSession session) {
        if (isLockedForScout(session)) {
            throw new BadRequestException("Cannot start a session that has already been submitted or completed.");
        }

        SessionStateMachine.assertTransition(session.getStatus(), SessionStatus.IN_PROGRESS, Role.SCOUT);
    }

    private void normalizePlanningStatus(ScoutingSession session) {
        if (session.getStatus() != SessionStatus.DRAFT && session.getStatus() != SessionStatus.NEW) {
            return;
        }

        session.setStatus(isPlanningComplete(session) ? SessionStatus.NEW : SessionStatus.DRAFT);
    }

    private boolean isPlanningComplete(ScoutingSession session) {
        return session.getSessionDate() != null
                && session.getScout() != null
                && session.getTargets() != null
                && !session.getTargets().isEmpty();
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
                    SessionStateMachine.assertTransition(other.getStatus(), SessionStatus.INCOMPLETE, farmAccessService.getCurrentUserRole());
                    other.markIncomplete();
                    other.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                    sessionAuditService.record(other, SessionAuditAction.SESSION_MARKED_INCOMPLETE,
                            "New session started while another was open", null, null, null, null);
                    sessionRepository.save(other);
                });
    }

    private boolean isVisibleToScout(ScoutingSession session) {
        return session.getStatus() != SessionStatus.DRAFT || session.isRemoteStartPending();
    }

    private boolean isVisibleToSuperAdmin(ScoutingSession session) {
        return EnumSet.of(
                SessionStatus.DRAFT,
                SessionStatus.NEW,
                SessionStatus.COMPLETED,
                SessionStatus.INCOMPLETE,
                SessionStatus.REOPENED
        ).contains(session.getStatus());
    }

    private boolean isRestrictedInProgressForFarmViewer(ScoutingSession session) {
        Role role = farmAccessService.getCurrentUserRole();
        return (role == Role.FARM_ADMIN || role == Role.MANAGER)
                && session.getStatus() == SessionStatus.IN_PROGRESS;
    }

    private void requireScoutRole(String message) {
        if (farmAccessService.getCurrentUserRole() != Role.SCOUT) {
            throw new ForbiddenException(message);
        }
    }

    private String resolveActorName(String actorNameOverride, User actor) {
        if (actorNameOverride != null && !actorNameOverride.isBlank()) {
            return actorNameOverride;
        }

        String fullName = String.join(" ",
                Optional.ofNullable(actor.getFirstName()).orElse(""),
                Optional.ofNullable(actor.getLastName()).orElse("")).trim();

        return fullName.isBlank() ? actor.getEmail() : fullName;
    }

    private void assertNotStale(Long requestedVersion, Long currentVersion, String entityName) {
        if (requestedVersion == null) {
            return;
        }
        if (!Objects.equals(requestedVersion, currentVersion)) {
            throw new ConflictException(entityName + " has changed on the server. Please sync and retry.");
        }
    }

    private void ensureDraftWorkspaceInitialized(ScoutingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            return;
        }
        if (observationDraftRepository.existsBySessionId(session.getId())) {
            return;
        }
        if (session.getObservations() == null || session.getObservations().isEmpty()) {
            return;
        }

        seedDraftObservationsFromCommitted(session);
    }

    private void seedDraftObservationsFromCommitted(ScoutingSession session) {
        observationDraftRepository.deleteBySessionId(session.getId());

        if (session.getObservations() == null || session.getObservations().isEmpty()) {
            return;
        }

        List<ScoutingObservationDraft> drafts = session.getObservations().stream()
                .filter(observation -> !observation.isDeleted())
                .map(observation -> ScoutingObservationDraft.builder()
                        .session(session)
                        .sessionTarget(observation.getSessionTarget())
                        .speciesCode(observation.getSpeciesCode())
                        .customSpecies(observation.getCustomSpecies())
                        .speciesIdentifier(observation.resolveSpeciesIdentifier())
                        .bayIndex(observation.getBayIndex())
                        .bayLabel(observation.getBayLabel())
                        .benchIndex(observation.getBenchIndex())
                        .benchLabel(observation.getBenchLabel())
                        .spotIndex(observation.getSpotIndex())
                        .count(observation.getCount())
                        .notes(observation.getNotes())
                        .clientRequestId(observation.getClientRequestId())
                        .syncStatus(SyncStatus.PENDING_UPLOAD)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        if (!drafts.isEmpty()) {
            observationDraftRepository.saveAll(drafts);
        }
    }

    private void promoteDraftObservationsToCommitted(ScoutingSession session) {
        List<ScoutingObservationDraft> drafts = observationDraftRepository.findBySessionId(session.getId()).stream()
                .filter(draft -> !draft.isDeleted())
                .toList();

        if (session.getObservations() != null && !session.getObservations().isEmpty()) {
            List<ScoutingObservation> existingObservations = new ArrayList<>(session.getObservations());
            existingObservations.forEach(session::removeObservation);
            observationRepository.flush();
        }

        drafts.forEach(draft -> session.addObservation(
                ScoutingObservation.builder()
                        .session(session)
                        .sessionTarget(draft.getSessionTarget())
                        .speciesCode(draft.getSpeciesCode())
                        .customSpecies(draft.getCustomSpecies())
                        .speciesIdentifier(draft.resolveSpeciesIdentifier())
                        .bayIndex(draft.getBayIndex())
                        .bayLabel(draft.getBayLabel())
                        .benchIndex(draft.getBenchIndex())
                        .benchLabel(draft.getBenchLabel())
                        .spotIndex(draft.getSpotIndex())
                        .count(draft.getCount())
                        .notes(draft.getNotes())
                        .clientRequestId(draft.getClientRequestId())
                        .syncStatus(SyncStatus.PENDING_UPLOAD)
                        .build()
        ));

        observationDraftRepository.deleteBySessionId(session.getId());
    }

    private void assertObservationSessionMatches(UUID sessionId, UpsertObservationRequest request) {
        if (request.sessionId() != null && !sessionId.equals(request.sessionId())) {
            throw new BadRequestException("Observation payload does not match session.");
        }
    }

    private ScoutingSessionTarget resolveObservationTarget(ScoutingSession session, UpsertObservationRequest request) {
        if (request.sessionTargetId() != null) {
            return sessionTargetRepository.findByIdAndSessionId(request.sessionTargetId(), session.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Session target not found"));
        }

        if (session.getTargets() != null && session.getTargets().size() == 1) {
            return session.getTargets().getFirst();
        }

        throw new BadRequestException("sessionTargetId is required when the scouting session has multiple sections.");
    }

    private int resolveSpotIndex(UpsertObservationRequest request) {
        return request.spotIndex() != null ? request.spotIndex() : 1;
    }

    private void applyObservationDraftValues(ScoutingObservationDraft observation,
                                             ScoutingSession session,
                                             ScoutingSessionTarget target,
                                             ResolvedObservationSpecies resolvedSpecies,
                                             Integer bayIndex,
                                             String bayTag,
                                             Integer benchIndex,
                                             String benchTag,
                                             Integer spotIndex,
                                             Integer count,
                                             String notes,
                                             UUID clientRequestId) {
        observation.setSession(session);
        observation.setSessionTarget(target);
        observation.setSpeciesCode(resolvedSpecies.speciesCode());
        observation.setCustomSpecies(resolvedSpecies.customSpecies());
        observation.setSpeciesIdentifier(resolvedSpecies.identifier());
        observation.setBayIndex(bayIndex);
        observation.setBayLabel(bayTag);
        observation.setBenchIndex(benchIndex);
        observation.setBenchLabel(benchTag);
        observation.setSpotIndex(spotIndex);
        observation.setCount(count);
        observation.setNotes(notes);
        observation.setClientRequestId(clientRequestId);
        observation.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        if (observation.isDeleted()) {
            observation.restore();
        }
        session.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        session.setUpdatedAt(LocalDateTime.now());
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

        ResolvedObservationSpecies resolvedSpecies = resolveObservationSpecies(session, request);

        Optional<ScoutingObservation> existingOpt = observationRepository
                .findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesIdentifier(
                        session.getId(),
                        target.getId(),
                        request.bayIndex(),
                        request.benchIndex(),
                        request.spotIndex(),
                        resolvedSpecies.identifier()
                );

        return existingOpt.orElseGet(() -> {
            ScoutingObservation created = ScoutingObservation.builder()
                    .session(session)
                    .sessionTarget(target)
                    .speciesCode(resolvedSpecies.speciesCode())
                    .customSpecies(resolvedSpecies.customSpecies())
                    .speciesIdentifier(resolvedSpecies.identifier())
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

    private List<ResolvedTarget> resolveTargets(CreateScoutingSessionRequest request, Farm farm) {
        List<SessionTargetRequest> requestedTargets = request.targets();
        if (requestedTargets == null || requestedTargets.isEmpty()) {
            return resolveDefaultTargets(farm);
        }

        return requestedTargets.stream()
                .map(target -> resolveTarget(target, farm))
                .toList();
    }

    private List<ResolvedTarget> resolveTargets(ScoutingSession sourceSession) {
        if (sourceSession.getTargets() == null || sourceSession.getTargets().isEmpty()) {
            return List.of();
        }

        Farm farm = sourceSession.getFarm();
        return sourceSession.getTargets().stream()
                .map(target -> new SessionTargetRequest(
                        target.getGreenhouse() != null ? target.getGreenhouse().getId() : null,
                        target.getFieldBlock() != null ? target.getFieldBlock().getId() : null,
                        target.getIncludeAllBays(),
                        target.getIncludeAllBenches(),
                        target.getBayTags() == null ? List.of() : List.copyOf(target.getBayTags()),
                        target.getBenchTags() == null ? List.of() : List.copyOf(target.getBenchTags()),
                        target.getAreaHectares()
                ))
                .map(target -> resolveTarget(target, farm))
                .toList();
    }

    private List<ResolvedTarget> resolveDefaultTargets(Farm farm) {
        List<ResolvedTarget> defaults = new ArrayList<>();

        greenhouseRepository.findByFarmId(farm.getId()).forEach(greenhouse ->
                defaults.add(new ResolvedTarget(greenhouse, null, true, true, List.of(), List.of(), null))
        );

        fieldBlockRepository.findByFarmId(farm.getId()).forEach(fieldBlock ->
                defaults.add(new ResolvedTarget(null, fieldBlock, true, true, List.of(), List.of(), null))
        );

        return List.copyOf(defaults);
    }

    private User resolveScout(CreateScoutingSessionRequest request, Farm farm) {
        if (request.scoutId() == null) {
            return validateAssignedScout(farm.getScout(), farm, "farm");
        }

        return resolveRequestedScout(request.scoutId(), farm);
    }

    private User resolveRequestedScout(UUID scoutId, Farm farm) {
        if (UNASSIGNED_USER_ID.equals(scoutId)) {
            return null;
        }

        User scout = userRepository.findById(scoutId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", scoutId));

        return validateAssignedScout(scout, farm, "requested");
    }

    private User resolveReusableScout(ScoutingSession sourceSession) {
        if (sourceSession.getScout() == null) {
            return null;
        }

        try {
            return validateAssignedScout(sourceSession.getScout(), sourceSession.getFarm(), "reused");
        } catch (BadRequestException exception) {
            log.info("Reused session {} will not keep scout assignment: {}", sourceSession.getId(), exception.getMessage());
            return null;
        }
    }

    private User validateAssignedScout(User scout, Farm farm, String source) {
        if (scout == null) {
            return null;
        }

        if (scout.getRole() != Role.SCOUT) {
            throw new BadRequestException("The " + source + " assigned user must have the SCOUT role.");
        }

        if (!scout.isActive()) {
            throw new BadRequestException("The " + source + " assigned scout is inactive or deleted.");
        }

        if (!belongsToFarm(scout, farm)) {
            throw new BadRequestException("The " + source + " assigned scout must belong to the same farm as the session.");
        }

        return scout;
    }

    private boolean belongsToFarm(User scout, Farm farm) {
        if (scout == null || scout.getId() == null || farm == null || farm.getId() == null) {
            return false;
        }

        return membershipRepository.findByUser_IdAndFarmId(scout.getId(), farm.getId())
                .filter(membership -> Boolean.TRUE.equals(membership.getIsActive()))
                .isPresent();
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
            if (!isVisibleToScout(session)) {
                throw new ForbiddenException("Draft sessions are only visible to managers and admins.");
            }
            enforceScoutOwnsSession(session);
            return;
        }

        requireSessionViewerAccess(session.getFarm());
        if (role == Role.SUPER_ADMIN && !isVisibleToSuperAdmin(session)) {
            throw new ForbiddenException("Active scouting sessions are only visible to the assigned scout.");
        }
    }

    private void enforceSessionOpenAccess(ScoutingSession session) {
        enforceSessionVisibility(session);
        if (isRestrictedInProgressForFarmViewer(session)) {
            throw new ForbiddenException("In-progress sessions are visible in the list but can only be opened by the assigned scout.");
        }
    }

    private void requireSessionViewerAccess(Farm farm) {
        Role role = farmAccessService.getCurrentUserRole();
        if (role == Role.SUPER_ADMIN) {
            return;
        }

        UUID currentUserId = currentUserService.getCurrentUserId();
        boolean isOwner = farm.getOwner() != null && farm.getOwner().getId().equals(currentUserId);
        boolean isMember = membershipRepository.existsByUser_IdAndFarmId(currentUserId, farm.getId());

        if (role == Role.FARM_ADMIN || role == Role.MANAGER) {
            if (isOwner || isMember) {
                return;
            }
        }

        if (role == Role.SCOUT && farm.getScout() != null && farm.getScout().getId().equals(currentUserId)) {
            return;
        }

        throw new ForbiddenException("You do not have permission to view this farm's sessions.");
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

    private ScoutingSessionDetailDto mapToViewerDetailDto(ScoutingSession session) {
        return mapToViewerDetailDto(session, false);
    }

    private ScoutingSessionDetailDto mapToViewerDetailDto(ScoutingSession session, boolean includeDeletedObservations) {
        if (isRestrictedInProgressForFarmViewer(session)) {
            return mapToRestrictedInProgressDto(session);
        }
        return mapToDetailDto(session, includeDeletedObservations);
    }

    private ScoutingSessionDetailDto mapToRestrictedInProgressDto(ScoutingSession session) {
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
                null,
                null,
                null,
                null,
                null,
                List.copyOf(session.getSurveySpecies()),
                List.of(),
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                session.getUpdatedAt(),
                false,
                null,
                List.of(),
                List.of()
        );
    }

    private ScoutingSessionDetailDto mapToDetailDto(ScoutingSession session, boolean includeDeletedObservations) {
        Map<UUID, List<ScoutingObservationDto>> observationsByTarget = resolveDisplayedObservationsByTarget(session, includeDeletedObservations);

        List<ScoutingSessionSectionDto> sectionDtos = session.getTargets().stream()
                .sorted(Comparator.comparing(target -> {
                    if (target.getGreenhouse() != null) {
                        return target.getGreenhouse().getName();
                    }
                    return target.getFieldBlock() != null ? target.getFieldBlock().getName() : "";
                }, String.CASE_INSENSITIVE_ORDER))
                .map(target -> {
                    List<ScoutingObservationDto> targetObservations = observationsByTarget
                            .getOrDefault(target.getId(), List.of());

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
                            targetObservations,
                            target.getAreaHectares(),
                            buildCoverage(target, targetObservations)
                    );
                })
                .toList();

        List<RecommendationEntryDto> recommendationDtos = session.getRecommendations().entrySet().stream()
                .map(entry -> new RecommendationEntryDto(entry.getKey(), entry.getValue()))
                .toList();
        List<CustomSpeciesDto> customSpeciesDtos = session.getCustomSurveySpecies() == null
                ? List.of()
                : session.getCustomSurveySpecies().stream()
                .map(this::mapToCustomSpeciesDto)
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
                List.copyOf(session.getSurveySpecies()),
                customSpeciesDtos,
                session.getDefaultPhotoSourceType(),
                session.getStartedAt(),
                session.getSubmittedAt(),
                session.getCompletedAt(),
                session.isRemoteStartPending(),
                session.getRemoteStartRequestedAt(),
                session.getRemoteStartRequestedByName(),
                session.getUpdatedAt(),
                session.isConfirmationAcknowledged(),
                session.getReopenComment(),
                sectionDtos,
                recommendationDtos
        );
    }

    private Map<UUID, List<ScoutingObservationDto>> resolveDisplayedObservationsByTarget(ScoutingSession session,
                                                                                         boolean includeDeletedObservations) {
        boolean useDrafts = shouldUseDraftObservations(session);
        if (useDrafts) {
            List<ScoutingObservationDraft> drafts = observationDraftRepository.findBySessionId(session.getId()).stream()
                    .filter(draft -> includeDeletedObservations || !draft.isDeleted())
                    .sorted(Comparator
                            .comparing(ScoutingObservationDraft::getBayIndex, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(ScoutingObservationDraft::getBenchIndex, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(ScoutingObservationDraft::getSpotIndex, Comparator.nullsLast(Integer::compareTo)))
                    .toList();

            if (!drafts.isEmpty()) {
                return drafts.stream()
                        .map(draft -> mapToObservationDto(draft, includeDeletedObservations))
                        .collect(Collectors.groupingBy(ScoutingObservationDto::sessionTargetId));
            }
        }

        return session.getObservations().stream()
                .filter(observation -> includeDeletedObservations || !observation.isDeleted())
                .sorted(Comparator
                        .comparing(ScoutingObservation::getBayIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ScoutingObservation::getBenchIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ScoutingObservation::getSpotIndex, Comparator.nullsLast(Integer::compareTo)))
                .map(observation -> mapToObservationDto(observation, includeDeletedObservations))
                .collect(Collectors.groupingBy(ScoutingObservationDto::sessionTargetId));
    }

    private boolean shouldUseDraftObservations(ScoutingSession session) {
        return farmAccessService.getCurrentUserRole() == Role.SCOUT
                && session.getStatus() != SessionStatus.COMPLETED
                && session.getStatus() != SessionStatus.CANCELLED;
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
                observation.getCustomSpecies() != null ? observation.getCustomSpecies().getId() : null,
                observation.getSpeciesDisplayName(),
                observation.resolveSpeciesIdentifier(),
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

    private ScoutingObservationDto mapToObservationDto(ScoutingObservationDraft observation) {
        return mapToObservationDto(observation, false);
    }

    private ScoutingObservationDto mapToObservationDto(ScoutingObservationDraft observation, boolean includeDeleted) {
        if (observation == null) {
            throw new IllegalArgumentException("Observation draft must not be null");
        }

        boolean deleted = includeDeleted && observation.isDeleted();

        return new ScoutingObservationDto(
                observation.getId(),
                observation.getVersion(),
                observation.getSession().getId(),
                observation.getSessionTarget().getId(),
                observation.getSessionTarget().getGreenhouse() != null ? observation.getSessionTarget().getGreenhouse().getId() : null,
                observation.getSessionTarget().getFieldBlock() != null ? observation.getSessionTarget().getFieldBlock().getId() : null,
                observation.getSpeciesCode(),
                observation.getCustomSpecies() != null ? observation.getCustomSpecies().getId() : null,
                observation.getSpeciesDisplayName(),
                observation.resolveSpeciesIdentifier(),
                observation.getCategory(),
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
                deleted ? observation.getDeletedAt() : null,
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
                .areaHectares(target.areaHectares())
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
        BigDecimal areaHectares = targetRequest.areaHectares();

        if (!includeAllBays && bayTags.isEmpty()) {
            throw new BadRequestException("Provide bayTags when includeAllBays is false.");
        }
        if (!includeAllBenches && benchTags.isEmpty()) {
            throw new BadRequestException("Provide benchTags when includeAllBenches is false.");
        }
        if (areaHectares != null && areaHectares.signum() < 0) {
            throw new BadRequestException("Target area cannot be negative.");
        }
        validateTargetSelections(greenhouse, fieldBlock, bayTags, benchTags);

        return new ResolvedTarget(greenhouse, fieldBlock, includeAllBays, includeAllBenches, bayTags, benchTags, areaHectares);
    }

    private BigDecimal calculateRequestedArea(List<ResolvedTarget> resolvedTargets) {
        return resolvedTargets.stream()
                .map(this::calculateTargetArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTargetArea(ResolvedTarget target) {
        if (target.areaHectares() != null) {
            return target.areaHectares();
        }

        int bayCount = target.greenhouse() != null
                ? target.greenhouse().resolvedBayCount()
                : target.fieldBlock().resolvedBayCount();
        int selectedBays = Boolean.TRUE.equals(target.includeAllBays()) ? bayCount : target.bayTags().size();
        BigDecimal structureArea = target.greenhouse() != null
                ? target.greenhouse().getAreaHectares()
                : target.fieldBlock().getAreaHectares();
        String structureName = target.greenhouse() != null
                ? target.greenhouse().getName()
                : target.fieldBlock().getName();

        return licenseService.calculateSelectedAreaHectares(
                structureArea,
                bayCount,
                Boolean.TRUE.equals(target.includeAllBays()),
                selectedBays,
                structureName
        );
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<SpeciesCode> normalizeSurveySpecies(List<SpeciesCode> surveySpeciesCodes) {
        if (surveySpeciesCodes == null || surveySpeciesCodes.isEmpty()) {
            return new ArrayList<>();
        }
        return surveySpeciesCodes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<CustomSpeciesDefinition> resolveCustomSurveySpecies(Farm farm, List<UUID> customSurveySpeciesIds) {
        if (customSurveySpeciesIds == null || customSurveySpeciesIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<UUID> uniqueIds = customSurveySpeciesIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (uniqueIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<CustomSpeciesDefinition> definitions = customSpeciesDefinitionRepository.findByFarmIdAndIdIn(farm.getId(), uniqueIds);
        Map<UUID, CustomSpeciesDefinition> definitionsById = definitions.stream()
                .collect(Collectors.toMap(CustomSpeciesDefinition::getId, definition -> definition));

        List<CustomSpeciesDefinition> orderedDefinitions = new ArrayList<>();
        for (UUID id : uniqueIds) {
            CustomSpeciesDefinition definition = definitionsById.get(id);
            if (definition == null) {
                throw new BadRequestException("Selected custom species does not belong to this farm.");
            }
            orderedDefinitions.add(definition);
        }

        return orderedDefinitions;
    }

    private ResolvedObservationSpecies resolveObservationSpecies(ScoutingSession session, UpsertObservationRequest request) {
        boolean hasBuiltInSpecies = request.speciesCode() != null;
        boolean hasCustomSpecies = request.customSpeciesId() != null;

        if (hasBuiltInSpecies == hasCustomSpecies) {
            throw new BadRequestException("Provide either a built-in speciesCode or a customSpeciesId.");
        }

        if (hasBuiltInSpecies) {
            SpeciesCode speciesCode = request.speciesCode();
            return new ResolvedObservationSpecies(
                    speciesCode,
                    null,
                    "CODE:" + speciesCode.name(),
                    speciesCode.getDisplayName(),
                    speciesCode.getCategory()
            );
        }

        CustomSpeciesDefinition customSpecies = customSpeciesDefinitionRepository
                .findByIdAndFarmId(request.customSpeciesId(), session.getFarm().getId())
                .orElseThrow(() -> new BadRequestException("Selected custom species does not belong to this farm."));

        return new ResolvedObservationSpecies(
                null,
                customSpecies,
                "CUSTOM:" + customSpecies.getId(),
                customSpecies.getName(),
                customSpecies.getCategory()
        );
    }

    private ResolvedObservationSpecies resolveObservationSpecies(ScoutingSession session,
                                                                 UpsertObservationRequest request,
                                                                 ScoutingObservationDraft existingObservation) {
        if (request.speciesCode() == null && request.customSpeciesId() == null) {
            return new ResolvedObservationSpecies(
                    existingObservation.getSpeciesCode(),
                    existingObservation.getCustomSpecies(),
                    existingObservation.resolveSpeciesIdentifier(),
                    existingObservation.getSpeciesDisplayName(),
                    existingObservation.getCategory()
            );
        }
        return resolveObservationSpecies(session, request);
    }

    private void assertSpeciesAllowed(ScoutingSession session, ResolvedObservationSpecies species) {
        boolean hasBuiltInSelection = session.getSurveySpecies() != null && !session.getSurveySpecies().isEmpty();
        boolean hasCustomSelection = session.getCustomSurveySpecies() != null && !session.getCustomSurveySpecies().isEmpty();

        if (!hasBuiltInSelection && !hasCustomSelection) {
            return;
        }

        if (species.speciesCode() != null && session.getSurveySpecies().contains(species.speciesCode())) {
            return;
        }

        if (species.customSpecies() != null) {
            boolean selectedCustomSpecies = session.getCustomSurveySpecies().stream()
                    .anyMatch(definition -> Objects.equals(definition.getId(), species.customSpecies().getId()));
            if (selectedCustomSpecies) {
                return;
            }

            if (allowsCustomSpeciesForCategory(session.getSurveySpecies(), species.category())) {
                return;
            }
        }

        throw new BadRequestException("Selected pest, disease, or beneficial insect is not configured for this session.");
    }

    private boolean allowsCustomSpeciesForCategory(List<SpeciesCode> selectedSpecies, ObservationCategory category) {
        if (selectedSpecies == null || selectedSpecies.isEmpty() || category == null) {
            return false;
        }

        return switch (category) {
            case PEST -> selectedSpecies.contains(SpeciesCode.PEST_OTHER);
            case DISEASE -> selectedSpecies.contains(SpeciesCode.DISEASE_OTHER);
            case BENEFICIAL -> selectedSpecies.contains(SpeciesCode.BENEFICIAL_OTHER);
        };
    }

    private CustomSpeciesDto mapToCustomSpeciesDto(CustomSpeciesDefinition definition) {
        return new CustomSpeciesDto(
                definition.getId(),
                definition.getCategory(),
                definition.getName(),
                definition.getCode()
        );
    }

    private void assertTargetSelectionsAllowCell(ScoutingSessionTarget target, String bayTag, String benchTag) {
        if (!Boolean.TRUE.equals(target.getIncludeAllBays()) && bayTag != null && !target.getBayTags().contains(bayTag)) {
            throw new BadRequestException("Selected bay is not part of this session target.");
        }
        if (!Boolean.TRUE.equals(target.getIncludeAllBenches()) && benchTag != null && !target.getBenchTags().contains(benchTag)) {
            throw new BadRequestException("Selected bench is not part of this session target.");
        }
    }

    private void validateTargetSelections(Greenhouse greenhouse,
                                          FieldBlock fieldBlock,
                                          List<String> bayTags,
                                          List<String> benchTags) {
        if (greenhouse != null) {
            List<String> availableBayTags = resolveGreenhouseBayTags(greenhouse);
            if (availableBayTags.isEmpty()) {
                throw new BadRequestException("Greenhouse must define at least one bay before it can be added to a scouting session.");
            }
            if (!bayTags.isEmpty() && !availableBayTags.containsAll(bayTags)) {
                throw new BadRequestException("Selected greenhouse bays do not exist.");
            }
            List<String> availableBedTags = greenhouse.resolvedBedTags();
            if (!benchTags.isEmpty() && !availableBedTags.containsAll(benchTags)) {
                throw new BadRequestException("Selected greenhouse beds do not exist.");
            }
        }

        if (fieldBlock != null) {
            List<String> availableBayTags = resolveFieldBayTags(fieldBlock);
            if (availableBayTags.isEmpty()) {
                throw new BadRequestException("Field must define at least one bay before it can be added to a scouting session.");
            }
            if (!bayTags.isEmpty() && !availableBayTags.containsAll(bayTags)) {
                throw new BadRequestException("Selected field bays do not exist.");
            }
        }
    }

    private ScoutingSectionCoverageDto buildCoverage(ScoutingSessionTarget target, List<ScoutingObservationDto> observations) {
        if (target.getGreenhouse() == null) {
            return null;
        }

        Greenhouse greenhouse = target.getGreenhouse();
        List<String> plannedBays = resolveSelectedGreenhouseBayTags(target, greenhouse);
        int totalBedCount = plannedBays.stream()
                .mapToInt(bayTag -> resolveSelectedBedTagsForBay(target, greenhouse, bayTag).size())
                .sum();

        Set<String> coveredBays = new LinkedHashSet<>();
        Set<String> coveredBeds = new LinkedHashSet<>();

        for (ScoutingObservationDto observation : observations) {
            String bayTag = observation.bayTag() != null
                    ? observation.bayTag()
                    : resolveTagByIndex(resolveGreenhouseBayTags(greenhouse), observation.bayIndex());
            if (bayTag == null || !plannedBays.contains(bayTag)) {
                continue;
            }

            coveredBays.add(bayTag);

            String bedTag = observation.benchTag() != null
                    ? observation.benchTag()
                    : resolveTagByIndex(resolveBedTagsForBay(greenhouse, bayTag), observation.benchIndex());
            if (bedTag == null) {
                continue;
            }

            if (resolveSelectedBedTagsForBay(target, greenhouse, bayTag).contains(bedTag)) {
                coveredBeds.add(bayTag + "::" + bedTag);
            }
        }

        return new ScoutingSectionCoverageDto(
                coveredBays.size(),
                plannedBays.size(),
                coveredBeds.size(),
                totalBedCount,
                totalBedCount > 0 && coveredBeds.size() >= totalBedCount
        );
    }

    private List<String> resolveSelectedGreenhouseBayTags(ScoutingSessionTarget target, Greenhouse greenhouse) {
        List<String> availableBayTags = resolveGreenhouseBayTags(greenhouse);
        if (Boolean.TRUE.equals(target.getIncludeAllBays())) {
            return availableBayTags;
        }
        return target.getBayTags().stream()
                .filter(availableBayTags::contains)
                .toList();
    }

    private List<String> resolveSelectedBedTagsForBay(ScoutingSessionTarget target, Greenhouse greenhouse, String bayTag) {
        List<String> availableBedTags = resolveBedTagsForBay(greenhouse, bayTag);
        if (Boolean.TRUE.equals(target.getIncludeAllBenches())) {
            return availableBedTags;
        }
        return target.getBenchTags().stream()
                .filter(availableBedTags::contains)
                .toList();
    }

    private List<String> resolveGreenhouseBayTags(Greenhouse greenhouse) {
        List<String> bayTags = greenhouse.resolvedBayTags();
        if (!bayTags.isEmpty()) {
            return bayTags;
        }
        return generateTags("Bay", greenhouse.resolvedBayCount());
    }

    private List<String> resolveFieldBayTags(FieldBlock fieldBlock) {
        if (fieldBlock.getBayTags() != null && !fieldBlock.getBayTags().isEmpty()) {
            return List.copyOf(fieldBlock.getBayTags());
        }
        return generateTags("Bay", fieldBlock.resolvedBayCount());
    }

    private List<String> resolveBedTagsForBay(Greenhouse greenhouse, String bayTag) {
        if (greenhouse.getBays() != null && !greenhouse.getBays().isEmpty()) {
            return greenhouse.getBays().stream()
                    .filter(bay -> bayTag.equals(bay.getBayTag()))
                    .findFirst()
                    .map(bay -> bay.getBedTags() != null && !bay.getBedTags().isEmpty()
                            ? List.copyOf(bay.getBedTags())
                            : generateTags("Bed", bay.getBedCount()))
                    .orElse(List.of());
        }
        return greenhouse.resolvedBedTags();
    }

    private List<String> generateTags(String prefix, int count) {
        if (count <= 0) {
            return List.of();
        }
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> prefix + "-" + index)
                .toList();
    }

    private String resolveTagByIndex(List<String> tags, Integer index) {
        if (tags == null || tags.isEmpty() || index == null || index < 1 || index > tags.size()) {
            return null;
        }
        return tags.get(index - 1);
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
            List<String> benchTags,
            BigDecimal areaHectares
    ) {
    }

    private record ResolvedObservationSpecies(
            SpeciesCode speciesCode,
            CustomSpeciesDefinition customSpecies,
            String identifier,
            String displayName,
            ObservationCategory category
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
