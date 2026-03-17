package mofo.com.pestscout.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserOnboardingScheduler {

    private final UserOnboardingService userOnboardingService;

    @Scheduled(cron = "${app.auth.invitation-expiry-cron:0 0 * * * *}")
    public void expireOverdueTemporaryPasswords() {
        long expired = userOnboardingService.expireOverdueTemporaryPasswordUsers();
        if (expired > 0) {
            log.info("Expired {} user onboarding profile(s) whose temporary password window elapsed.", expired);
        }
    }
}
