package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.farm.repository.FarmRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseLifecycleScheduler {

    private final FarmRepository farmRepository;
    private final LicenseExpiryNotificationService licenseExpiryNotificationService;

    @Scheduled(cron = "${app.licensing.notification-cron:0 0 7 * * *}")
    public void queueExpiredFarmNotifications() {
        long queued = farmRepository.findAll().stream()
                .filter(licenseExpiryNotificationService::shouldQueueExpiryNotice)
                .peek(licenseExpiryNotificationService::queueExpiryNotice)
                .count();

        if (queued > 0) {
            log.info("Queued {} license expiry notification batch(es).", queued);
        }
    }
}
