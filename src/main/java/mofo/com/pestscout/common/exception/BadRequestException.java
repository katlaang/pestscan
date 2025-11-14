package mofo.com.pestscout.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signals that the client has supplied an invalid request. Creation is logged so malformed request patterns can be traced.
 */
public class BadRequestException extends RuntimeException implements ErrorCodeCarrier {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadRequestException.class);

    /**
     * Creates a new {@link BadRequestException} with the supplied descriptive message.
     *
     * @param message explanation describing the validation failure
     */
    public BadRequestException(String message) {
        super(message);
        LOGGER.warn("BadRequestException raised: {}", message);
    }

    /**
     * Provides the machine-readable error code associated with bad request scenarios.
     *
     * @return the constant {@code BAD_REQUEST} error code
     */
    @Override
    public String getErrorCode() {
        return "BAD_REQUEST";
    }
}
