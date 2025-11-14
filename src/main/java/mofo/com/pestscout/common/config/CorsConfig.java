package mofo.com.pestscout.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Provides centralized Cross-Origin Resource Sharing configuration so that the React Native and React frontends can
 * communicate with the backend. The configuration values are externalized to application properties and logged during
 * bean initialization.
 */
@Configuration
public class CorsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age}")
    private long maxAge;

    /**
     * Builds the {@link CorsConfigurationSource} bean using the configured values. The method logs the derived
     * configuration components to aid in troubleshooting environment-specific deployments.
     *
     * @return a fully configured {@link CorsConfigurationSource} instance used by Spring Web MVC
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);
        LOGGER.info("Configured CORS allowed origins: {}", origins);

        List<String> methods = Arrays.asList(allowedMethods.split(","));
        configuration.setAllowedMethods(methods);
        LOGGER.info("Configured CORS allowed methods: {}", methods);

        if ("*".equals(allowedHeaders)) {
            configuration.addAllowedHeader("*");
            LOGGER.info("Configured CORS allowed headers: wildcard");
        } else {
            List<String> headers = Arrays.asList(allowedHeaders.split(","));
            configuration.setAllowedHeaders(headers);
            LOGGER.info("Configured CORS allowed headers: {}", headers);
        }

        configuration.setAllowCredentials(allowCredentials);
        LOGGER.info("Configured CORS allow credentials: {}", allowCredentials);

        configuration.setMaxAge(maxAge);
        LOGGER.info("Configured CORS max age: {}", maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        LOGGER.debug("Initialized CorsConfigurationSource bean");

        return source;
    }
}
