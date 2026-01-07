package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.scouting.model.SessionAuditAction;
import mofo.com.pestscout.scouting.model.SessionAuditEvent;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.repository.SessionAuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionAuditService {

    private final SessionAuditEventRepository auditRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public void record(ScoutingSession session,
                       SessionAuditAction action,
                       String comment,
                       String deviceId,
                       String deviceType,
                       String location,
                       String actorNameOverride) {

        User actor = currentUserService.getCurrentUser();
        String actorName = Optional.ofNullable(actorNameOverride)
                .filter(name -> !name.isBlank())
                .orElseGet(() -> String.join(" ",
                        Optional.ofNullable(actor.getFirstName()).orElse(""),
                        Optional.ofNullable(actor.getLastName()).orElse("")).trim());

        SessionAuditEvent event = SessionAuditEvent.builder()
                .session(session)
                .farm(session.getFarm())
                .action(action)
                .actorName(actorName.isBlank() ? actor.getEmail() : actorName)
                .actorEmail(actor.getEmail())
                .actorRole(actor.getRole())
                .deviceId(deviceId)
                .deviceType(deviceType)
                .location(location)
                .comment(comment)
                .occurredAt(LocalDateTime.now())
                .syncStatus(SyncStatus.PENDING_UPLOAD)
                .build();

        auditRepository.save(event);
        log.debug("Recorded audit event {} for session {}", action, session.getId());
    }
}
