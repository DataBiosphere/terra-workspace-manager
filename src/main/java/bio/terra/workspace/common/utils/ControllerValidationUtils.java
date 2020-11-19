package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.service.datareference.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import java.util.ArrayList;
import java.util.List;

public final class ControllerValidationUtils {

  public static void validatePaginationParams(int offset, int limit) {
    List<String> errors = new ArrayList<>();
    if (offset < 0) {
      errors.add("offset must be greater than or equal to 0.");
    }
    if (limit < 1) {
      errors.add("limit must be greater than or equal to 1.");
    }
    if (!errors.isEmpty()) {
      throw new ValidationException("Invalid pagination parameters.", errors);
    }
  }

  public static void validateCreateDataReferenceRequestBody(CreateDataReferenceRequestBody body) {
    if (body.getResourceId() != null) {
      throw new ControlledResourceNotImplementedException(
          "Unable to create a reference with a resourceId, use a reference type and description"
              + " instead. This functionality will be implemented in the future.");
    }
    if (body.getReferenceType() == null || body.getReference() == null) {
      throw new InvalidDataReferenceException(
          "Data reference must contain a reference type and a reference description");
    }
    // TODO: remove this check when we add support for resource-specific credentials.
    if (body.getCredentialId() != null) {
      throw new InvalidDataReferenceException(
          "Resource-specific credentials are not supported yet.");
    }
  }
}
