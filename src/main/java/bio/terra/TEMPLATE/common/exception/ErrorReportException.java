package bio.terra.TEMPLATE.common.exception;

// This base class has data that corresponds to the ErrorReport model generated from
// the OpenAPI yaml. The global exception handler auto-magically converts exceptions
// of this base class into the appropriate ErrorReport REST response.

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.http.HttpStatus;

import java.util.List;

public abstract class ErrorReportException extends RuntimeException {
    private final List<String> causes;
    private final HttpStatus statusCode;

    public ErrorReportException(String message) {
        super(message);
        this.causes = null;
        this.statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ErrorReportException(String message, Throwable cause) {
        super(message, cause);
        this.causes = null;
        this.statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ErrorReportException(Throwable cause) {
        super(cause);
        this.causes = null;
        this.statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ErrorReportException(String message, List<String> causes, HttpStatus statusCode) {
        super(message);
        this.causes = causes;
        this.statusCode = statusCode;
    }

    public ErrorReportException(String message, Throwable cause, List<String> causes, HttpStatus statusCode) {
        super(message, cause);
        this.causes = causes;
        this.statusCode = statusCode;
    }

    public List<String> getCauses() {
        return causes;
    }

    public HttpStatus getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("causes", causes)
                .append("statusCode", statusCode)
                .toString();
    }
}
