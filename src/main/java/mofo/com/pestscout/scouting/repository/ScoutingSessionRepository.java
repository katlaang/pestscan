package mofo.com.pestscout.scouting.repository;

import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.model.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for scouting sessions.
 */
public interface ScoutingSessionRepository extends JpaRepository<ScoutingSession, UUID> {

    /**
     * Return all sessions for a farm.
     * Suitable for small farms or internal batch jobs.
     */
    List<ScoutingSession> findByFarmId(UUID farmId);

    /**
     * Return a paged list of sessions for a farm.
     * Intended for dashboards and list screens in the UI.
     */
    Page<ScoutingSession> findByFarmId(UUID farmId, Pageable pageable);

    /**
     * Load a session by id and verify that it belongs to the given farm.
     * Used in service-level authorization checks.
     */
    Optional<ScoutingSession> findByIdAndFarmId(UUID sessionId, UUID farmId);

    /**
     * Find all sessions for a farm within a date range.
     * Used for weekly or monthly reports and trend analysis.
     */
    List<ScoutingSession> findByFarmIdAndSessionDateBetween(
            UUID farmId,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Count how many sessions a farm has in a given status
     * (for example, completed vs draft sessions).
     */
    long countByFarmIdAndStatus(UUID farmId, SessionStatus status);

    /**
     * Change feed helper for offline-first sync flows.
     */
    List<ScoutingSession> findByFarmIdAndUpdatedAtAfter(UUID farmId, LocalDateTime updatedAfter);
}


