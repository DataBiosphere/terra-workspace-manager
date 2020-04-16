package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public final class ControllerValidationUtils {

  private static final List<String> VALID_FILTER_CONTROLLED_OPTIONS =
      Arrays.asList("controlled", "uncontrolled", "all");

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

  public static void ValidateFilterParams(String filterControlled) {
    if (!StringUtils.isEmpty(filterControlled)
        && !VALID_FILTER_CONTROLLED_OPTIONS.contains(filterControlled)) {
      throw new ValidationException(
          String.format(
              "filterControlled must be one of: (%s)",
              String.join(", ", VALID_FILTER_CONTROLLED_OPTIONS)));
    }
  }
}
