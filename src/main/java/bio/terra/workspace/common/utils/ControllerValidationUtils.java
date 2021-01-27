package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataReferenceInfo;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Various utilities for validating requests in Controllers. */
public final class ControllerValidationUtils {

  private static Logger logger = LoggerFactory.getLogger(ControllerValidationUtils.class);

  // Pattern shared with Sam, originally from https://www.regular-expressions.info/email.html.
  public static final Pattern EMAIL_VALIDATION_PATTERN =
      Pattern.compile("(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$");

  /**
   * Utility to validate limit/offset parameters used in pagination.
   *
   * <p>This throws ValidationExceptions if invalid offset or limit values are provided. This only
   * asserts that offset is at least 0 and limit is at least 1. More specific validation can be
   * added for individual endpoints.
   */
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

  /**
   * Utility function for validating a CreateDataReferenceRequestBody.
   *
   * <p>CreateDataReferenceRequestBody holds one of several types of data reference. This enforces
   * that exactly one type of reference is present, and that it matches the specified ReferenceType
   * field.
   */
  public static void validate(CreateDataReferenceRequestBody body) {
    ReferenceTypeEnum referenceType = body.getReferenceType();
    DataReferenceInfo info = body.getReferenceInfo();
    final boolean valid;
    switch (referenceType) {
      case DATA_REPO_SNAPSHOT:
        valid =
            ((info.getDataRepoSnapshot() == null && body.getReference() == null)
                || info.getBigQueryDataset() != null
                || info.getDataRepoSnapshot() != null);
        break;
      case GOOGLE_BUCKET:
        valid =
            (info.getGoogleBucket() == null
                || info.getBigQueryDataset() != null
                || info.getDataRepoSnapshot() != null
                || body.getReference() != null);
        break;
      case BIG_QUERY_DATASET:
        valid =
            (info.getBigQueryDataset() == null
                || info.getGoogleBucket() != null
                || info.getDataRepoSnapshot() != null
                || body.getReference() != null);
        break;
      default:
        throw new InvalidDataReferenceException("Unknown reference type specified");
    }
    if (!valid) {
      throw new InvalidDataReferenceException(
          "Exactly one field of ReferenceInfo must be set, and it should match ReferenceType");
    }
  }

  /**
   * Validate that a user-provided string matches the format of an email address.
   *
   * <p>This only validates the email addresses format, not whether it exists, what domain it's
   * from, etc.
   */
  public static void validateEmail(String email) {
    if (!EMAIL_VALIDATION_PATTERN.matcher(email).matches()) {
      logger.warn("User provided invalid email for group or user: " + email);
      throw new ValidationException("Invalid user or group email provided, see logs for details");
    }
  }
}
