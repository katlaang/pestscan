package mofo.com.pestscout.auth.service;

import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.dto.SessionStreamEventResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class ClientSessionEventService {

    private static final long STREAM_TIMEOUT_MILLIS = 0L;
    private final ConcurrentMap<SessionEmitterKey, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId, String clientSessionId) {
        SessionEmitterKey key = new SessionEmitterKey(userId, clientSessionId);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        SseEmitter previousEmitter = emitters.put(key, emitter);
        if (previousEmitter != null) {
            previousEmitter.complete();
        }

        emitter.onCompletion(() -> emitters.remove(key, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(key, emitter);
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.debug("Session event stream failed for user {} session {}: {}",
                    userId, clientSessionId, ex.getMessage());
            emitters.remove(key, emitter);
            emitter.complete();
        });

        sendEvent(key, emitter, "connected", new SessionStreamEventResponse(
                "connected",
                "Session event stream connected",
                clientSessionId,
                Instant.now()
        ), false);

        return emitter;
    }

    public void notifySessionReplacedAfterCommit(UUID userId, String previousClientSessionId, String activeClientSessionId) {
        if (userId == null
                || previousClientSessionId == null
                || previousClientSessionId.isBlank()
                || previousClientSessionId.equals(activeClientSessionId)) {
            return;
        }

        Runnable publishTask = () -> publishSessionReplaced(userId, previousClientSessionId, activeClientSessionId);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
            return;
        }

        publishTask.run();
    }

    @Scheduled(fixedDelayString = "${app.auth.session-stream-heartbeat-ms:25000}")
    public void publishHeartbeats() {
        emitters.forEach((key, emitter) -> sendEvent(key, emitter, "heartbeat", new SessionStreamEventResponse(
                "heartbeat",
                "keepalive",
                key.clientSessionId(),
                Instant.now()
        ), false));
    }

    private void publishSessionReplaced(UUID userId, String previousClientSessionId, String activeClientSessionId) {
        SessionEmitterKey key = new SessionEmitterKey(userId, previousClientSessionId);
        SseEmitter emitter = emitters.remove(key);
        if (emitter == null) {
            return;
        }

        sendEvent(key, emitter, "session-replaced", new SessionStreamEventResponse(
                "session-replaced",
                "Your session was opened elsewhere. Please log in again.",
                activeClientSessionId,
                Instant.now()
        ), true);
    }

    private void sendEvent(SessionEmitterKey key,
                           SseEmitter emitter,
                           String eventName,
                           SessionStreamEventResponse payload,
                           boolean completeAfterSend) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload));
            if (completeAfterSend) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(key, emitter);
            log.debug("Closing session event stream for user {} session {} after send failure: {}",
                    key.userId(), key.clientSessionId(), ex.getMessage());
            try {
                emitter.completeWithError(ex);
            } catch (IllegalStateException ignored) {
                emitter.complete();
            }
        }
    }

    private record SessionEmitterKey(UUID userId, String clientSessionId) {
    }
}
