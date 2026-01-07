package mofo.com.pestscout.scouting.repository;

import mofo.com.pestscout.scouting.model.SessionAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionAuditEventRepository extends JpaRepository<SessionAuditEvent, UUID> {
    List<SessionAuditEvent> findBySessionIdOrderByOccurredAtAsc(UUID sessionId);
}
