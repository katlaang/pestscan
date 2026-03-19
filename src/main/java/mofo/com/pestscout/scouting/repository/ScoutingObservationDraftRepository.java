package mofo.com.pestscout.scouting.repository;

import mofo.com.pestscout.scouting.model.ScoutingObservationDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoutingObservationDraftRepository extends JpaRepository<ScoutingObservationDraft, UUID> {
    List<ScoutingObservationDraft> findBySessionId(UUID sessionId);

    List<ScoutingObservationDraft> findBySessionIdInAndUpdatedAtAfter(List<UUID> sessionIds, LocalDateTime since);

    Optional<ScoutingObservationDraft> findByIdAndSessionId(UUID observationDraftId, UUID sessionId);

    Optional<ScoutingObservationDraft> findByClientRequestId(UUID clientRequestId);

    Optional<ScoutingObservationDraft> findBySessionIdAndSessionTargetIdAndBayIndexAndBenchIndexAndSpotIndexAndSpeciesIdentifier(
            UUID sessionId,
            UUID sessionTargetId,
            Integer bayIndex,
            Integer benchIndex,
            Integer spotIndex,
            String speciesIdentifier
    );

    boolean existsBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
