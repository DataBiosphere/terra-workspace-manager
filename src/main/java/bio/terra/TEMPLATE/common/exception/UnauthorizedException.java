package bio.terra.TEMPLATE.common.exception;

// This base class has data that corresponds to the ErrorReport model generated from
// the OpenAPI yaml. The global exception handler auto-magically converts exceptions
// of this base class into the appropriate ErrorReport REST response.

import org.springframework.http.HttpStatus;

import java.util.List;

public abstract class UnauthorizedException extends ErrorReportException {
    private static final HttpStatus thisStatus = HttpStatus.UNAUTHORIZED;

    public UnauthorizedException(String message) {
        super(message, null, thisStatus);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause, null, thisStatus);
    }

    public UnauthorizedException(Throwable cause) {
        super(null, cause, null, thisStatus);
    }

    public UnauthorizedException(String message, List<String> causes) {
        super(message, causes, thisStatus);
    }

    public UnauthorizedException(String message, Throwable cause, List<String> causes) {
        super(message, cause, causes, thisStatus);
    }
}
