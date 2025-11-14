package mofo.com.pestscout.common.exception;

public class UnauthorizedException extends RuntimeException implements ErrorCodeCarrier {

    public UnauthorizedException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return "UNAUTHORIZED";
    }
}
