package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.ConflictException;

/**
 * An operation (create, update, delete) is in progress on the resource, so another operation cannot
 * be started.
 */
public class ResourceIsBusyException extends ConflictException {

  public ResourceIsBusyException(String message) {
    super(message);
  }
}
