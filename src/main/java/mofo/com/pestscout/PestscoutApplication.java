package mofo.com.pestscout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Pestscout Spring Boot application. Bootstraps the application context and logs lifecycle events.
 */
@SpringBootApplication
public class PestscoutApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(PestscoutApplication.class);

    /**
     * Launches the Spring Boot application and records a startup log entry.
     *
     * @param args runtime arguments passed to the application process
     */
    public static void main(String[] args) {
        LOGGER.info("Starting Pestscout Spring Boot application");
        SpringApplication.run(PestscoutApplication.class, args);
    }
}
