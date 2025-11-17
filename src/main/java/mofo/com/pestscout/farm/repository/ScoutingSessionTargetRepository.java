package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.ScoutingSessionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoutingSessionTargetRepository extends JpaRepository<ScoutingSessionTarget, UUID> {
    Optional<ScoutingSessionTarget> findByIdAndSessionId(UUID id, UUID sessionId);
}
