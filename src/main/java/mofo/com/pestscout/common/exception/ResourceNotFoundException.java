package mofo.com.pestscout.common.exception;

public class ResourceNotFoundException extends RuntimeException implements ErrorCodeCarrier {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return "NOT_FOUND";
    }
}
