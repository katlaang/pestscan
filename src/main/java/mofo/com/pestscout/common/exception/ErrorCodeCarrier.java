package mofo.com.pestscout.common.exception;

/**
 * Marker interface for application-level exceptions that expose a machine-readable error code.
 */
public interface ErrorCodeCarrier {

    /**
     * Returns the application-specific error code that categorizes the associated exception.
     *
     * @return non-null error code representing the failure condition
     */
    String getErrorCode();
}
