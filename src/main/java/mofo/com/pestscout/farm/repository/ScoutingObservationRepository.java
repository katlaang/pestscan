package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.ScoutingObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoutingObservationRepository extends JpaRepository<ScoutingObservation, UUID> {

    List<ScoutingObservation> findBySession_IdIn(Collection<UUID> sessionIds);

    Optional<ScoutingObservation> findByIdAndSession_Id(UUID observationId, UUID sessionId);
}
