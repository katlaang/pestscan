package mofo.com.pestscout.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Indicates that a requested entity could not be located. Creation is logged to highlight missing resources during
 * troubleshooting.
 */
public class ResourceNotFoundException extends RuntimeException implements ErrorCodeCarrier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceNotFoundException.class);

    /**
     * Creates a new {@link ResourceNotFoundException} with a descriptive message about the missing resource.
     *
     * @param message text describing which resource could not be found
     */
    public ResourceNotFoundException(String message) {
        super(message);
        LOGGER.warn("ResourceNotFoundException raised: {}", message);
    }

    /**
     * Returns the error code representing not found conditions.
     *
     * @return the {@code NOT_FOUND} error code string
     */
    @Override
    public String getErrorCode() {
        return "NOT_FOUND";
    }
}
