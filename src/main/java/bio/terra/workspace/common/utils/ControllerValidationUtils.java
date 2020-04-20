package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;

public final class ControllerValidationUtils {

  public static void ValidatePaginationParams(int offset, int limit) {
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
}
