package mofo.com.pestscout.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import mofo.com.pestscout.common.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized REST exception translator that maps domain-level exceptions to standardized {@link ErrorResponse}
 * payloads and appropriate HTTP status codes. Logging provides insight into error flows for operations teams.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles custom application exceptions by converting them into consistent {@link ErrorResponse} payloads while
     * logging the error context.
     *
     * @param ex      the runtime exception raised by the domain or service layer
     * @param request HTTP request that triggered the exception
     * @return response entity containing the structured error response
     */
    @ExceptionHandler({
            BadRequestException.class,
            ConflictException.class,
            ResourceNotFoundException.class,
            ForbiddenException.class,
            UnauthorizedException.class
    })
    public ResponseEntity<ErrorResponse> handleApplicationExceptions(RuntimeException ex, HttpServletRequest request) {
        HttpStatus status = resolveStatus(ex);
        LOGGER.warn("Handling application exception [type={}, status={}, path={}]", ex.getClass().getSimpleName(), status, request.getRequestURI());
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .errorCode(resolveErrorCode(ex))
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Processes validation errors thrown by Spring MVC binding and translates them into a detailed response object.
     *
     * @param ex      exception containing validation failure information
     * @param request HTTP request associated with the validation errors
     * @return response entity containing validation error information
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.toList());

        LOGGER.warn("Validation failure encountered [path={}, errorCount={}]", request.getRequestURI(), details.size());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode("VALIDATION_ERROR")
                .message("Request validation failed")
                .path(request.getRequestURI())
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catches any unexpected exceptions and logs them at error level before returning a generic error response.
     *
     * @param ex      unhandled exception
     * @param request associated HTTP request
     * @return response entity representing an internal server error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unhandled exception encountered [path={}]", request.getRequestURI(), ex);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Resolves the HTTP status code that corresponds to a specific runtime exception type.
     *
     * @param ex exception requiring translation into HTTP status semantics
     * @return matching HTTP status value
     */
    private HttpStatus resolveStatus(RuntimeException ex) {
        if (ex instanceof BadRequestException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof ConflictException) {
            return HttpStatus.CONFLICT;
        }
        if (ex instanceof ResourceNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof ForbiddenException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof UnauthorizedException) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Extracts the error code from custom exceptions or falls back to a default value.
     *
     * @param ex runtime exception providing contextual error information
     * @return error code string to include in the response payload
     */
    private String resolveErrorCode(RuntimeException ex) {
        if (ex instanceof ErrorCodeCarrier carrier) {
            return carrier.getErrorCode();
        }
        return "APPLICATION_ERROR";
    }

    /**
     * Formats validation field errors into user-friendly strings.
     *
     * @param error validation error reported by Spring
     * @return formatted field error message
     */
    private String formatFieldError(FieldError error) {
        return "%s: %s".formatted(error.getField(), error.getDefaultMessage());
    }
}
