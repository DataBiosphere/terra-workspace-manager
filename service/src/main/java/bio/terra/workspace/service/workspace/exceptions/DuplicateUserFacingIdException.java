package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;

/** A workspace with this workspace_id already exists. */
public class DuplicateUserFacingIdException extends BadRequestException {
    public DuplicateUserFacingIdException(String message) {
        super(message);
    }

    public DuplicateUserFacingIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
