package mofo.com.pestscout.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thrown when authentication fails or credentials are missing. Creation is logged to trace security concerns without
 * exposing sensitive data.
 */
public class UnauthorizedException extends RuntimeException implements ErrorCodeCarrier {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnauthorizedException.class);

    /**
     * Constructs a new {@link UnauthorizedException} instance with a descriptive message about the failure.
     *
     * @param message summary describing why authorization failed
     */
    public UnauthorizedException(String message) {
        super(message);
        LOGGER.warn("UnauthorizedException raised: {}", message);
    }

    /**
     * Provides the standardized error code used for unauthorized access attempts.
     *
     * @return the {@code UNAUTHORIZED} error code string
     */
    @Override
    public String getErrorCode() {
        return "UNAUTHORIZED";
    }
}
