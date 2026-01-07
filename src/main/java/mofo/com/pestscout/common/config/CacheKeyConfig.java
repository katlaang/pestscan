package mofo.com.pestscout.common.config;

import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.farm.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheKeyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheKeyConfig.class);

    @Bean("tenantAwareKeyGenerator")
    public KeyGenerator tenantAwareKeyGenerator(CurrentUserService currentUserService) {
        return (target, method, params) -> {
            String tenant = "anonymous";
            String userId = "anonymous";
            String role = "anonymous";

            try {
                User user = currentUserService.getCurrentUser();
                tenant = user.getCustomerNumber();
                userId = user.getId() != null ? user.getId().toString() : "unknown";
                role = user.getRole() != null ? user.getRole().name() : "unknown";
            } catch (RuntimeException ex) {
                LOGGER.debug(
                        "Falling back to anonymous cache key for {}.{}",
                        target.getClass().getSimpleName(),
                        method.getName()
                );
            }

            Object paramKey = SimpleKeyGenerator.generateKey(params);
            return String.format(
                    "%s::%s::%s::tenant=%s::user=%s::role=%s",
                    target.getClass().getSimpleName(),
                    method.getName(),
                    paramKey,
                    tenant,
                    userId,
                    role
            );
        };
    }


}

