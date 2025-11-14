package mofo.com.pestscout.common.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Standard JSON payload returned for error conditions.
 */
public class ErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String errorCode;
    private final String message;
    private final String path;
    private final List<String> details;

    private ErrorResponse(Builder builder) {
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.status = builder.status;
        this.errorCode = builder.errorCode;
        this.message = builder.message;
        this.path = builder.path;
        this.details = builder.details != null ? List.copyOf(builder.details) : Collections.emptyList();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public List<String> getDetails() {
        return details;
    }

    public static Builder builder() {
        return new Builder();
    }

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

    public static final class Builder {
        private Instant timestamp;
        private int status;
        private String errorCode;
        private String message;
        private String path;
        private List<String> details;

        private Builder() {
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder details(List<String> details) {
            this.details = details;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(this);
        }
    }
}
