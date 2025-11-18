package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FieldBlock;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Create a new scouting session for a farm.
     * The manager assigns a scout and defines basic metadata.
     */
    @Transactional
    public ScoutingSessionDetailDto createSession(CreateScoutingSessionRequest request) {
        Farm farm = farmRepository.findById(request.farmId())
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", request.farmId()));

        farmAccessService.requireAdminOrSuperAdmin(farm);

        User manager = resolveManager(farm);
        User scout = farm.getScout();

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
                .status(SessionStatus.DRAFT)
                .confirmationAcknowledged(false)
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();

        request.targets().forEach(targetRequest -> session.addTarget(buildTarget(targetRequest, farm)));

        ScoutingSession saved = sessionRepository.save(session);
        log.info("Created scouting session {} for farm {}", saved.getId(), farm.getId());
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
            session.getTargets().clear();
            request.targets().forEach(targetRequest -> session.addTarget(buildTarget(targetRequest, session.getFarm())));
        }

        ScoutingSession saved = sessionRepository.save(session);
        log.info("Updated scouting session {}", saved.getId());
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

        farmAccessService.requireAdminOrSuperAdmin(session.getFarm());

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Cannot start a session that has already been completed.");
        }

        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        session.setStatus(SessionStatus.IN_PROGRESS);

        ScoutingSession saved = sessionRepository.save(session);
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

        if (request == null || !Boolean.TRUE.equals(request.confirmationAcknowledged())) {
            throw new BadRequestException("Please confirm all information is correct before completing the session.");
        }

        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setConfirmationAcknowledged(true);

        ScoutingSession saved = sessionRepository.save(session);
        return mapToDetailDto(saved);
    }

    /**
     * Reopen a completed session so that observations can be edited again.
     */
    @Transactional
    public ScoutingSessionDetailDto reopenSession(UUID sessionId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        farmAccessService.requireAdminOrSuperAdmin(session.getFarm());

        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BadRequestException("Only completed sessions can be reopened.");
        }

        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setConfirmationAcknowledged(false);
        session.setCompletedAt(null);

        ScoutingSession saved = sessionRepository.save(session);
        return mapToDetailDto(saved);
    }

    /**
     * Create or update a single observation cell (bay, bench, spot, species).
     * If a row exists, its count and notes are updated; otherwise a new row is created.
     */
    @Transactional
    public ScoutingObservationDto upsertObservation(UUID sessionId, UpsertObservationRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        farmAccessService.requireScoutOfFarm(session.getFarm());
        ensureSessionEditableForObservations(session);

        if (!sessionId.equals(request.sessionId())) {
            throw new BadRequestException("Observation payload does not match session.");
        }

        ScoutingSessionTarget target = sessionTargetRepository.findByIdAndSessionId(request.sessionTargetId(), sessionId)
                .orElseThrow(() -> new BadRequestException("Session target not found for this session."));
        assertTargetSelectionsAllowCell(target, request.bayTag(), request.benchTag());

        if (request.speciesCode() == null) {
            throw new BadRequestException("Species must be provided for an observation.");
        }

        var existingOpt = observationRepository
                .findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                        sessionId,
                        target.getId(),
                        request.bayIndex(),
                        request.benchIndex(),
                        request.spotIndex(),
                        request.speciesCode()
                );

        ScoutingObservation observation = existingOpt.orElseGet(() -> {
            ScoutingObservation created = ScoutingObservation.builder()
                    .session(session)
                    .sessionTarget(target)
                    .speciesCode(request.speciesCode())
                    .bayIndex(request.bayIndex())
                    .bayLabel(request.bayTag())
                    .benchIndex(request.benchIndex())
                    .benchLabel(request.benchTag())
                    .spotIndex(request.spotIndex())
                    .build();
            session.addObservation(created);
            return created;
        });

        observation.setCount(request.count());
        observation.setNotes(request.notes());
        observation.setSessionTarget(target);
        observation.setBayLabel(request.bayTag());
        observation.setBenchLabel(request.benchTag());

        ScoutingObservation saved = observationRepository.save(observation);
        return mapToObservationDto(saved);
    }

    /**
     * Delete a single observation from a session.
     */
    @Transactional
    public void deleteObservation(UUID sessionId, UUID observationId) {
        ScoutingObservation observation = observationRepository
                .findByIdAndSessionId(observationId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingObservation", "id", observationId));

        farmAccessService.requireScoutOfFarm(observation.getSession().getFarm());
        ensureSessionEditableForObservations(observation.getSession());
        observation.getSession().removeObservation(observation);
        observationRepository.delete(observation);
    }

    /**
     * Load one session with all its observations and recommendations.
     */
    @Transactional(readOnly = true)
    public ScoutingSessionDetailDto getSession(UUID sessionId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));
        farmAccessService.requireViewAccess(session.getFarm());
        return mapToDetailDto(session);
    }

    /**
     * List all sessions for a farm, newest first.
     */
    @Transactional(readOnly = true)
    public List<ScoutingSessionDetailDto> listSessions(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
        farmAccessService.requireViewAccess(farm);

        return sessionRepository.findByFarmId(farmId).stream()
                .sorted(Comparator.comparing(ScoutingSession::getSessionDate).reversed())
                .map(this::mapToDetailDto)
                .collect(Collectors.toList());
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
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Completed sessions must be reopened before editing.");
        }
    }

    /**
     * Only sessions in DRAFT or IN_PROGRESS can have their observations changed.
     */
    private void ensureSessionEditableForObservations(ScoutingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new BadRequestException("Completed or cancelled sessions cannot be edited.");
        }
    }

    private User resolveManager(Farm farm) {
        if (farmAccessService.isSuperAdmin()) {
            return farm.getOwner();
        }
        return currentUserService.getCurrentUser();
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
        Map<UUID, List<ScoutingObservation>> observationsByTarget = session.getObservations().stream()
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
                            .map(this::mapToObservationDto)
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
                null, // version, if you add @Version to BaseEntity you can expose it here
                session.getFarm().getId(),
                session.getSessionDate(),
                session.getWeekNumber(),
                session.getStatus(),
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
                session.getCompletedAt(),
                session.isConfirmationAcknowledged(),
                sectionDtos,
                recommendationDtos
        );
    }

    /**
     * Convert an observation entity into a DTO for the API.
     */
    private ScoutingObservationDto mapToObservationDto(ScoutingObservation observation) {
        ObservationCategory category = observation.getCategory(); // derived from speciesCode in the entity

        return new ScoutingObservationDto(
                observation.getId(),
                null, // version placeholder
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
                observation.getNotes()
        );
    }

    private ScoutingSessionTarget buildTarget(SessionTargetRequest targetRequest, Farm farm) {
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

        return ScoutingSessionTarget.builder()
                .greenhouse(greenhouse)
                .fieldBlock(fieldBlock)
                .includeAllBays(includeAllBays)
                .includeAllBenches(includeAllBenches)
                .bayTags(bayTags)
                .benchTags(benchTags)
                .build();
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

    private int resolveWeekNumber(java.time.LocalDate date, Integer requestedWeek) {
        if (requestedWeek != null) {
            return requestedWeek;
        }
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return date.get(weekFields.weekOfWeekBasedYear());
    }
}
