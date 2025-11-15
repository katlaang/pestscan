package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.SessionStatus;
import mofo.com.pestscout.farm.model.ScoutingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScoutingSessionRepository extends JpaRepository<ScoutingSession, UUID> {

    List<ScoutingSession> findByFarm_Id(UUID farmId);

    List<ScoutingSession> findByFarm_IdAndSessionDateBetween(
            UUID farmId,
            LocalDate startDate,
            LocalDate endDate);

    long countByFarm_IdAndStatus(UUID farmId, SessionStatus status);
}
