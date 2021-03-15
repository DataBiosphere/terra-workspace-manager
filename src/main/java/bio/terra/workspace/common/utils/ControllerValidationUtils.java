package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateDataReferenceRequestBody;
import bio.terra.workspace.service.resource.controlled.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.workspace.exceptions.AzureNotImplementedException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Various utilities for validating requests in Controllers. */
public final class ControllerValidationUtils {

  private static final Logger logger = LoggerFactory.getLogger(ControllerValidationUtils.class);

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
   * Utility function for validating a ApiCreateDataReferenceRequestBody.
   *
   * <p>ApiCreateDataReferenceRequestBody is currently structured to allow several parameters for
   * controlled and private resources that aren't supported in WM. This function throws exceptions
   * if any of those fields are set, or if any required fields are missing.
   */
  // TODO(PF-404): remove this once ApiCreateDataReferenceRequestBody is removed.
  public static void validate(ApiCreateDataReferenceRequestBody body) {
    if (body.getResourceId() != null) {
      throw new ControlledResourceNotImplementedException(
          "Unable to create a reference with a resourceId, use a reference type and description"
              + " instead. This functionality will be implemented in the future.");
    }
    if (body.getReferenceType() == null || body.getReference() == null) {
      throw new InvalidReferenceException(
          "Data reference must contain a reference type and a reference description");
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

  /** Validate that a user is requesting a valid cloud for adding workspace context. */
  public static void validateCloudPlatform(ApiCloudPlatform platform) {
    if (platform != ApiCloudPlatform.GCP) {
      throw new AzureNotImplementedException(
          "Invalid cloud platform. Currently, only GCP is supported.");
    }
  }
}
