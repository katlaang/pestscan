package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.common.config.RuntimeMode;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EdgeSyncScheduler {

    private final RuntimeMode runtimeMode;
    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingPhotoRepository photoRepository;

    @Scheduled(fixedDelayString = "${app.edge.sync.interval-ms:30000}")
    public void syncPendingEdgeChanges() {
        if (runtimeMode.isCloud()) {
            return;
        }

        long pendingSessions = sessionRepository.countBySyncStatus(SyncStatus.PENDING_UPLOAD);
        long pendingPhotos = photoRepository.countBySyncStatus(SyncStatus.PENDING_UPLOAD);

        if (pendingSessions == 0 && pendingPhotos == 0) {
            return;
        }

        log.info("EDGE sync pending — sessions: {}, photos: {}. Implement transport to cloud sync endpoints to upload.",
                pendingSessions, pendingPhotos);
    }
}
