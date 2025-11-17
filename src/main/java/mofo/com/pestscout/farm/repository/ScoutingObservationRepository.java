package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.ScoutingObservation;
import mofo.com.pestscout.farm.model.SpeciesCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for observations recorded during scouting sessions.
 */
public interface ScoutingObservationRepository extends JpaRepository<ScoutingObservation, UUID> {

    /**
     * Load all observations belonging to any of the given sessions.
     * Used when building reports or heatmaps across multiple sessions.
     */
    List<ScoutingObservation> findBySessionIdIn(Collection<UUID> sessionIds);

    /**
     * Load all observations for a single session.
     * Used when showing or exporting one session in detail.
     */
    List<ScoutingObservation> findBySessionId(UUID sessionId);

    /**
     * Find one observation by its id and verify that it belongs to the given session.
     * This prevents a client from editing an observation in another session by id only.
     */
    Optional<ScoutingObservation> findByIdAndSessionId(UUID observationId, UUID sessionId);

    /**
     * Find an observation at a specific grid cell for a specific species within a session.
     * This supports the "upsert cell" operation: if a row exists, update its count; if not, insert a new one.
     */
    Optional<ScoutingObservation> findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesCode(
            UUID sessionId,
            UUID sessionTargetId,
            Integer bayIndex,
            Integer benchIndex,
            Integer spotIndex,
            SpeciesCode speciesCode
    );
}
