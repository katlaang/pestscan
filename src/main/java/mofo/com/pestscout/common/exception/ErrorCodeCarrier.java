package mofo.com.pestscout.common.exception;

/**
 * Marker interface for application-level exceptions that expose a machine-readable error code.
 */
public interface ErrorCodeCarrier {

    String getErrorCode();
}
