package mofo.com.pestscout.common.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Canonical error payload returned by REST controllers. The structure is immutable and logs creation events for
 * observability.
 */
public class ErrorResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorResponse.class);

    private final Instant timestamp;
    private final int status;
    private final String errorCode;
    private final String message;
    private final String path;
    private final List<String> details;

    /**
     * Creates an error response from the supplied builder values.
     *
     * @param builder source builder containing response attributes
     */
    private ErrorResponse(Builder builder) {
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.status = builder.status;
        this.errorCode = builder.errorCode;
        this.message = builder.message;
        this.path = builder.path;
        this.details = builder.details != null ? List.copyOf(builder.details) : Collections.emptyList();
        LOGGER.debug("Constructed ErrorResponse [status={}, errorCode={}, path={}]", status, errorCode, path);
    }

    /**
     * Returns the timestamp indicating when the error response was created.
     *
     * @return instant of error response creation
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the HTTP status associated with the error.
     *
     * @return HTTP status code as an integer
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the machine-readable error code.
     *
     * @return application-specific error code string
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the human-readable error message.
     *
     * @return descriptive error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the request path that triggered the error.
     *
     * @return URI path from the originating request
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns additional error details such as validation messages.
     *
     * @return immutable list of supplementary error descriptions
     */
    public List<String> getDetails() {
        return details;
    }

    /**
     * Creates a builder for {@link ErrorResponse} instances.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder seeded from an existing error response.
     *
     * @param template error response used as the initial value source
     * @return builder initialized with the template values
     */
    public static Builder builder(ErrorResponse template) {
        Objects.requireNonNull(template, "template");
        return new Builder()
                .timestamp(template.timestamp)
                .status(template.status)
                .errorCode(template.errorCode)
                .message(template.message)
                .path(template.path)
                .details(template.details);
    }

    /**
     * Fluent builder for {@link ErrorResponse} instances. Logs when payloads are produced to aid diagnostics.
     */
    public static final class Builder {
        private Instant timestamp;
        private int status;
        private String errorCode;
        private String message;
        private String path;
        private List<String> details;

        /**
         * Creates a new builder instance.
         */
        private Builder() {
        }

        /**
         * Sets the timestamp for the error response.
         *
         * @param timestamp moment the error should report
         * @return current builder for chaining
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the HTTP status code.
         *
         * @param status HTTP status value as an integer
         * @return current builder for chaining
         */
        public Builder status(int status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the machine-readable error code.
         *
         * @param errorCode code describing the error condition
         * @return current builder for chaining
         */
        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        /**
         * Sets the human-readable error message.
         *
         * @param message textual explanation of the error condition
         * @return current builder for chaining
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Sets the request path where the error occurred.
         *
         * @param path URI path that triggered the error response
         * @return current builder for chaining
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets additional error details such as validation messages.
         *
         * @param details list of supplementary diagnostic messages
         * @return current builder for chaining
         */
        public Builder details(List<String> details) {
            this.details = details;
            return this;
        }

        /**
         * Produces an immutable {@link ErrorResponse} instance and logs the creation event.
         *
         * @return constructed error response
         */
        public ErrorResponse build() {
            ErrorResponse response = new ErrorResponse(this);
            LOGGER.debug("Built ErrorResponse via builder [status={}, errorCode={}]", status, errorCode);
            return response;
        }
    }
}
