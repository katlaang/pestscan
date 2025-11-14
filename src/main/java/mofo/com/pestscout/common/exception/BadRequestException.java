package mofo.com.pestscout.common.exception;

public class BadRequestException extends RuntimeException implements ErrorCodeCarrier {

    public BadRequestException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return "BAD_REQUEST";
    }
}
