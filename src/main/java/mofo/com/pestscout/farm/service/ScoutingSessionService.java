package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.ObservationRequest;
import mofo.com.pestscout.farm.dto.ObservationResponse;
import mofo.com.pestscout.farm.dto.ScoutingSessionRequest;
import mofo.com.pestscout.farm.dto.ScoutingSessionResponse;
import mofo.com.pestscout.farm.dto.SessionCompletionRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.ObservationCategory;
import mofo.com.pestscout.farm.model.RecommendationType;
import mofo.com.pestscout.farm.model.ScoutingObservation;
import mofo.com.pestscout.farm.model.ScoutingSession;
import mofo.com.pestscout.farm.model.SessionStatus;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.ScoutingObservationRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoutingSessionService {

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final FarmRepository farmRepository;
    private final UserRepository userRepository;

    @Transactional
    public ScoutingSessionResponse createSession(ScoutingSessionRequest request) {
        Farm farm = farmRepository.findById(request.getFarmId())
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", request.getFarmId()));

        User manager = resolveUser(request.getManagerId());
        User scout = resolveUser(request.getScoutId());

        ScoutingSession session = ScoutingSession.builder()
                .farm(farm)
                .manager(manager)
                .scout(scout)
                .sessionDate(request.getSessionDate())
                .cropType(request.getCropType())
                .cropVariety(request.getCropVariety())
                .weather(request.getWeather())
                .notes(request.getNotes())
                .status(SessionStatus.DRAFT)
                .confirmationAcknowledged(false)
                .recommendations(copyRecommendations(request.getRecommendations()))
                .build();

        return mapToResponse(sessionRepository.save(session));
    }

    @Transactional
    public ScoutingSessionResponse updateSession(UUID sessionId, ScoutingSessionRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Completed sessions must be reopened before editing.");
        }

        if (!session.getFarm().getId().equals(request.getFarmId())) {
            throw new BadRequestException("Cannot move session to another farm");
        }

        session.setSessionDate(request.getSessionDate());
        session.setCropType(request.getCropType());
        session.setCropVariety(request.getCropVariety());
        session.setWeather(request.getWeather());
        session.setNotes(request.getNotes());
        session.setRecommendations(copyRecommendations(request.getRecommendations()));

        session.setManager(resolveUser(request.getManagerId()));
        session.setScout(resolveUser(request.getScoutId()));

        return mapToResponse(sessionRepository.save(session));
    }

    @Transactional
    public ScoutingSessionResponse startSession(UUID sessionId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Cannot start a session that has already been completed.");
        }

        session.setStatus(SessionStatus.IN_PROGRESS);
        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        return mapToResponse(sessionRepository.save(session));
    }

    @Transactional
    public ScoutingSessionResponse completeSession(UUID sessionId, SessionCompletionRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Session is already completed.");
        }

        if (request == null || !request.isConfirmationAcknowledged()) {
            throw new BadRequestException("Please confirm all information is correct before completing the session.");
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        session.setConfirmationAcknowledged(request.isConfirmationAcknowledged());
        return mapToResponse(sessionRepository.save(session));
    }

    @Transactional
    public ScoutingSessionResponse reopenSession(UUID sessionId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BadRequestException("Only completed sessions can be reopened.");
        }

        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setConfirmationAcknowledged(false);
        session.setCompletedAt(null);
        return mapToResponse(sessionRepository.save(session));
    }

    @Transactional
    public ObservationResponse addObservation(UUID sessionId, ObservationRequest request) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        ensureSessionEditable(session);
        validateObservation(request);

        ScoutingObservation observation = ScoutingObservation.builder()
                .session(session)
                .category(request.getCategory())
                .pestType(request.getPestType())
                .diseaseType(request.getDiseaseType())
                .bayIndex(request.getBayIndex())
                .benchIndex(request.getBenchIndex())
                .spotIndex(request.getSpotIndex())
                .count(request.getCount())
                .notes(request.getNotes())
                .build();

        session.addObservation(observation);
        sessionRepository.save(session);
        return mapToObservationResponse(observation);
    }

    @Transactional
    public ObservationResponse updateObservation(UUID sessionId, UUID observationId, ObservationRequest request) {
        ScoutingObservation observation = observationRepository.findByIdAndSession_Id(observationId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingObservation", "id", observationId));

        ensureSessionEditable(observation.getSession());
        validateObservation(request);

        observation.setCategory(request.getCategory());
        observation.setPestType(request.getPestType());
        observation.setDiseaseType(request.getDiseaseType());
        observation.setBayIndex(request.getBayIndex());
        observation.setBenchIndex(request.getBenchIndex());
        observation.setSpotIndex(request.getSpotIndex());
        observation.setCount(request.getCount());
        observation.setNotes(request.getNotes());

        return mapToObservationResponse(observationRepository.save(observation));
    }

    @Transactional
    public void deleteObservation(UUID sessionId, UUID observationId) {
        ScoutingObservation observation = observationRepository.findByIdAndSession_Id(observationId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingObservation", "id", observationId));

        ensureSessionEditable(observation.getSession());
        observation.getSession().removeObservation(observation);
        observationRepository.delete(observation);
    }

    @Transactional(readOnly = true)
    public ScoutingSessionResponse getSession(UUID sessionId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));
        return mapToResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ScoutingSessionResponse> listSessions(UUID farmId) {
        return sessionRepository.findByFarm_Id(farmId).stream()
                .sorted(Comparator.comparing(ScoutingSession::getSessionDate).reversed())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countCompletedSessionsThisWeek(UUID farmId) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        LocalDateTime now = LocalDateTime.now();
        return sessionRepository.findByFarm_Id(farmId).stream()
                .filter(session -> session.getStatus() == SessionStatus.COMPLETED)
                .filter(session -> session.getCompletedAt() != null)
                .filter(session -> session.getCompletedAt().get(weekFields.weekOfWeekBasedYear()) == now.get(weekFields.weekOfWeekBasedYear()))
                .count();
    }

    private void ensureSessionEditable(ScoutingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Reopen the session to edit observations.");
        }
    }

    private User resolveUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void validateObservation(ObservationRequest request) {
        if (request.getCategory() == ObservationCategory.PEST && request.getPestType() == null) {
            throw new BadRequestException("Pest observations require a pest type");
        }
        if (request.getCategory() == ObservationCategory.DISEASE && request.getDiseaseType() == null) {
            throw new BadRequestException("Disease observations require a disease type");
        }
        if (request.getCategory() == ObservationCategory.BENEFICIAL && request.getPestType() != null) {
            throw new BadRequestException("Beneficial observations should not specify a pest type");
        }
    }

    private Map<RecommendationType, String> copyRecommendations(Map<RecommendationType, String> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return new EnumMap<>(RecommendationType.class);
        }
        return new EnumMap<>(recommendations);
    }

    private ScoutingSessionResponse mapToResponse(ScoutingSession session) {
        List<ObservationResponse> observations = session.getObservations().stream()
                .sorted(Comparator
                        .comparing(ScoutingObservation::getBayIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ScoutingObservation::getBenchIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ScoutingObservation::getSpotIndex, Comparator.nullsLast(Integer::compareTo)))
                .map(this::mapToObservationResponse)
                .collect(Collectors.toList());

        return ScoutingSessionResponse.builder()
                .id(session.getId())
                .farmId(session.getFarm().getId())
                .farmName(session.getFarm().getName())
                .managerId(session.getManager() != null ? session.getManager().getId() : null)
                .scoutId(session.getScout() != null ? session.getScout().getId() : null)
                .managerName(session.getManager() != null ? session.getManager().getFirstName() + " " + session.getManager().getLastName() : null)
                .scoutName(session.getScout() != null ? session.getScout().getFirstName() + " " + session.getScout().getLastName() : null)
                .sessionDate(session.getSessionDate())
                .cropType(session.getCropType())
                .cropVariety(session.getCropVariety())
                .weather(session.getWeather())
                .notes(session.getNotes())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .confirmationAcknowledged(session.isConfirmationAcknowledged())
                .recommendations(session.getRecommendations())
                .observations(observations)
                .build();
    }

    private ObservationResponse mapToObservationResponse(ScoutingObservation observation) {
        return ObservationResponse.builder()
                .id(observation.getId())
                .category(observation.getCategory())
                .pestType(observation.getPestType())
                .diseaseType(observation.getDiseaseType())
                .bayIndex(observation.getBayIndex())
                .benchIndex(observation.getBenchIndex())
                .spotIndex(observation.getSpotIndex())
                .count(observation.getCount() != null ? observation.getCount() : 0)
                .notes(observation.getNotes())
                .build();
    }
}
