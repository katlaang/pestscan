package mofo.com.pestscout.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a state conflict such as duplicate resources or violated unique constraints. Creation is logged to assist
 * with diagnosing concurrency or data integrity issues.
 */
public class ConflictException extends RuntimeException implements ErrorCodeCarrier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConflictException.class);

    /**
     * Builds a new {@link ConflictException} with a contextual description of the conflict.
     *
     * @param message human-readable detail about the conflict condition
     */
    public ConflictException(String message) {
        super(message);
        LOGGER.warn("ConflictException raised: {}", message);
    }

    /**
     * Returns the dedicated error code for conflict scenarios.
     *
     * @return the {@code CONFLICT} error code string
     */
    @Override
    public String getErrorCode() {
        return "CONFLICT";
    }
}
