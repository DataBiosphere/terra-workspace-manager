package bio.terra.TEMPLATE.service.ping.exception;

import bio.terra.TEMPLATE.common.exception.BadRequestException;

import java.util.List;

// TODO: TEMPLATE: In general, prefer specific exceptions to general exceptions.
//  The PingService throws this exception. It is caught by the global exception
//  handler and turned into an ErrorReport response.

public class BadPingException extends BadRequestException {

    public BadPingException(String message) {
        super(message);
    }

    public BadPingException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadPingException(Throwable cause) {
        super(cause);
    }

    public BadPingException(String message, List<String> causes) {
        super(message, causes);
    }

    public BadPingException(String message, Throwable cause, List<String> causes) {
        super(message, cause, causes);
    }
}
