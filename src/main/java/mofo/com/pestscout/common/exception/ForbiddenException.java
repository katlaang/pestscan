package mofo.com.pestscout.common.exception;

public class ForbiddenException extends RuntimeException implements ErrorCodeCarrier {

    public ForbiddenException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return "FORBIDDEN";
    }
}
