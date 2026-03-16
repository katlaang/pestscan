package mofo.com.pestscout.farm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Central licensing policy settings so trial, paid, extension, and post-expiry visibility
 * windows can be tuned without code changes.
 */
@Component
@ConfigurationProperties(prefix = "app.licensing")
@Getter
@Setter
public class LicensePolicyProperties {

    private int trialMonths = 3;
    private int maxTrialExtensionMonths = 3;
    private int paidMonths = 12;
    private int maxPaidExtensionMonths = 6;
    private int dashboardVisibilityDaysAfterExpiry = 30;
    private String notificationCron = "0 0 7 * * *";
    private String publicBaseUrl = "http://localhost:8080";
}
