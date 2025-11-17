package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.*;
import mofo.com.pestscout.farm.model.*;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.ScoutingObservationRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoutingSessionService {

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final FarmRepository farmRepository;
    private final UserRepository userRepository;

    /**
     * Create a new scouting session for a farm.
     * The manager assigns a scout and defines basic metadata.
     */
    @Transactional
    public ScoutingSessionDetailDto createSession(CreateScoutingSessionRequest request) {
        Farm farm = farmRepository.findById(request.farmId())
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", request.farmId()));

        User manager = resolveUser(request.managerId());
        User scout = resolveUser(request.scoutId());

        ScoutingSession session = ScoutingSession.builder()
                .farm(farm)
                .manager(manager)
                .scout(scout)
                .sessionDate(request.sessionDate())
                .cropType(request.cropType())
                .cropVariety(request.cropVariety())
                .weather(request.weather())
                .notes(request.notes())
                .status(SessionStatus.DRAFT)
                .confirmationAcknowledged(false)
                .recommendations(copyRecommendations(request.recommendations()))
                .build();

        ScoutingSession saved = sessionRepository.save(session);
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

        ensureSessionEditableForMetadata(session);

        if (request.sessionDate() != null) {
            session.setSessionDate(request.sessionDate());
        }
        if (request.cropType() != null) {
            session.setCropType(request.cropType());
        }
        if (request.cropVariety() != null) {
            session.setCropVariety(request.cropVariety());
        }
        if (request.weather() != null) {
            session.setWeather(request.weather());
        }
        if (request.notes() != null) {
            session.setNotes(request.notes());
        }
        if (request.recommendations() != null) {
            session.setRecommendations(copyRecommendations(request.recommendations()));
        }

        if (request.managerId() != null) {
            session.setManager(resolveUser(request.managerId()));
        }
        if (request.scoutId() != null) {
            session.setScout(resolveUser(request.scoutId()));
        }

        ScoutingSession saved = sessionRepository.save(session);
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

        ensureSessionEditableForObservations(session);

        if (request.speciesCode() == null) {
            throw new BadRequestException("Species must be provided for an observation.");
        }

        var existingOpt = observationRepository
                .findBySessionIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
                        sessionId,
                        request.bayIndex(),
                        request.benchIndex(),
                        request.spotIndex(),
                        request.speciesCode()
                );

        ScoutingObservation observation = existingOpt.orElseGet(() -> {
            ScoutingObservation created = ScoutingObservation.builder()
                    .session(session)
                    .speciesCode(request.speciesCode())
                    .bayIndex(request.bayIndex())
                    .benchIndex(request.benchIndex())
                    .spotIndex(request.spotIndex())
                    .build();
            session.addObservation(created);
            return created;
        });

        observation.setCount(request.count());
        observation.setNotes(request.notes());

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
        return mapToDetailDto(session);
    }

    /**
     * List all sessions for a farm, newest first.
     */
    @Transactional(readOnly = true)
    public List<ScoutingSessionDetailDto> listSessions(UUID farmId) {
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

    private User resolveUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
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
        List<ScoutingObservationDto> observationDtos = session.getObservations().stream()
                .sorted(Comparator
                        .comparing(ScoutingObservation::getBayIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ScoutingObservation::getBenchIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ScoutingObservation::getSpotIndex, Comparator.nullsLast(Integer::compareTo)))
                .map(this::mapToObservationDto)
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
                null, // greenhouseId if you add that relation
                null, // fieldBlockId if you add that relation
                session.getSessionDate(),
                session.getStatus(),
                managerId,
                scoutId,
                session.getCropType(),
                session.getCropVariety(),
                session.getWeather(),
                session.getNotes(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.isConfirmationAcknowledged(),
                observationDtos,
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
                observation.getSpeciesCode(),
                category,
                observation.getBayIndex(),
                observation.getBenchIndex(),
                observation.getSpotIndex(),
                observation.getCount() != null ? observation.getCount() : 0,
                observation.getNotes()
        );
    }
}
