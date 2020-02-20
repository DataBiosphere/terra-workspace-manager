package bio.terra.TEMPLATE.service.migrate.exception;

import bio.terra.TEMPLATE.common.exception.InternalServerErrorException;

import java.util.List;

public class MigrateException extends InternalServerErrorException {
    public MigrateException(String message) {
        super(message);
    }

    public MigrateException(String message, Throwable cause) {
        super(message, cause);
    }

    public MigrateException(Throwable cause) {
        super(cause);
    }

    public MigrateException(String message, List<String> causes) {
        super(message, causes);
    }

    public MigrateException(String message, Throwable cause, List<String> causes) {
        super(message, cause, causes);
    }
}
