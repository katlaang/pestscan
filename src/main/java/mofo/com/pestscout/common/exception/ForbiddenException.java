package mofo.com.pestscout.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raised when a user is authenticated but lacks the necessary permissions. Creation is logged to support
 * authorization troubleshooting.
 */
public class ForbiddenException extends RuntimeException implements ErrorCodeCarrier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForbiddenException.class);

    /**
     * Instantiates a new {@link ForbiddenException} that carries a descriptive message.
     *
     * @param message message describing the authorization failure
     */
    public ForbiddenException(String message) {
        super(message);
        LOGGER.warn("ForbiddenException raised: {}", message);
    }

    /**
     * Supplies the standardized error code for forbidden actions.
     *
     * @return the {@code FORBIDDEN} error code string
     */
    @Override
    public String getErrorCode() {
        return "FORBIDDEN";
    }
}
