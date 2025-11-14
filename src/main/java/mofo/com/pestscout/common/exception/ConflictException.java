package mofo.com.pestscout.common.exception;

public class ConflictException extends RuntimeException implements ErrorCodeCarrier {

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return "CONFLICT";
    }
}
