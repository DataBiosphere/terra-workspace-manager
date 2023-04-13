package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceCategory;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.workspace.exceptions.CloudPlatformNotImplementedException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO-Dex move azure utils to a new class

/** Various utilities for validating requests in Controllers. */
public final class ControllerValidationUtils {

  private static final Logger logger = LoggerFactory.getLogger(ControllerValidationUtils.class);

  // Pattern shared with Sam, originally from https://www.regular-expressions.info/email.html.
  public static final Pattern EMAIL_VALIDATION_PATTERN =
      Pattern.compile("(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$");

  /**
   * Property keys must be 1-1024 characters, using letters, numbers, dashes, and underscores and
   * must not start with a dash or underscore.
   */
  public static final Pattern PROPERTY_KEY_VALIDATION_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][-_a-zA-Z0-9]{0,1023}$");

  /**
   * userFacingId must be 3-63 characters, use lower-case letters, numbers, dashes, or underscores
   * and must start with a lower-case letter or number.
   */
  public static final Pattern USER_FACING_ID_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-_a-z0-9]{2,62}$");

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
   * Validate that a user-provided string matches the format of an email address.
   *
   * <p>This only validates the email addresses format, not whether it exists, what domain it's
   * from, etc.
   */
  public static void validateEmail(String email) {
    // matcher does not support null, so explicitly defend against that case
    if (email == null) {
      logger.warn("User provided null email");
      throw new ValidationException("Missing required email");
    }
    if (!EMAIL_VALIDATION_PATTERN.matcher(email).matches()) {
      logger.warn("User provided invalid email for group or user: " + email);
      throw new ValidationException("Invalid email provided");
    }
  }

  public static void validatePropertyKey(String key) {
    if (key == null) {
      logger.warn("User provided null property key");
      throw new ValidationException("Missing required property key");
    }
    if (!PROPERTY_KEY_VALIDATION_PATTERN.matcher(key).matches()) {
      logger.warn("User provided invalid property key: " + key);
      throw new ValidationException("Invalid property key provided");
    }
  }

  /** Validate that a user is requesting a valid cloud for adding workspace context. */
  public static void validateCloudPlatform(ApiCloudPlatform platform) {
    switch (platform) {
      case GCP:
      case AZURE:
      case AWS:
        break;
      default:
        throw new CloudPlatformNotImplementedException(
            "Invalid cloud platform. Currently, only AZURE and GCP are supported.");
    }
  }

  public static void validateUserFacingId(String userFacingId) {
    if (userFacingId == null) {
      logger.warn("userFacingId cannot be null");
      // "ID" instead of "userFacingId" because user sees this.
      throw new ValidationException("ID must be set");
    }
    if (!USER_FACING_ID_VALIDATION_PATTERN.matcher(userFacingId).matches()) {
      logger.warn("User provided invalid userFacingId: " + userFacingId);
      // "ID" instead of "userFacingId" because user sees this.
      throw new ValidationException(
          "ID must have 3-63 characters, contain lowercase letters, numbers, dashes, or underscores, and start with lowercase letter or number");
    }
  }

  /** Return the appropriate IAM action for creating the specified controlled resource in Sam. */
  public static String samCreateAction(ControlledResourceFields commonFields) {
    return samCreateAction(commonFields.getAccessScope(), commonFields.getManagedBy());
  }

  public static String samCreateAction(
      AccessScopeType accessScopeType, ManagedByType managedByType) {
    return ControlledResourceCategory.get(accessScopeType, managedByType)
        .getSamCreateResourceAction();
  }

  /**
   * Validate the format of an ipAddress or range of addresses for Azure SAS tokens. We can't do
   * this directly in the generated spring code yet, because the swagger codegen plugin doesn't
   * support the use of oneOf in schema generation.
   *
   * @param ipRange a single ip address, or a range of ip addresses separated by a dash ('-')
   */
  public static void validateIpAddressRange(@Nullable String ipRange) {
    if (ipRange == null) {
      return;
    }
    var addresses = ipRange.split("-");
    var validator = InetAddressValidator.getInstance();
    for (var address : addresses) {
      if (!validator.isValid(address)) {
        throw new ValidationException("Invalid ip address or ip address range: " + ipRange);
      }
    }
  }

  /**
   * Validate that the expiration duration (in seconds) is between 1 and the maximum allowed
   * duration (in minutes).
   *
   * @param sasExpirationDuration user-specified duration in seconds (note that null is allowed)
   * @param maxDurationMinutes maximum allowed duration in minutes
   * @throws ValidationException if sasExpiration is not positive or is greater than maximum allowed
   *     duration. Does not throw an exception if sasExpiration is null.
   */
  public static void validateSasExpirationDuration(
      @Nullable Long sasExpirationDuration, Long maxDurationMinutes) {
    if (sasExpirationDuration == null) {
      return;
    }
    if (sasExpirationDuration <= 0) {
      throw new ValidationException(
          "sasExpirationDuration must be positive: " + sasExpirationDuration);
    }
    long maxDurationSeconds = 60 * maxDurationMinutes;
    if (sasExpirationDuration > maxDurationSeconds) {
      throw new ValidationException(
          String.format(
              "sasExpirationDuration cannot be greater than allowed maximum (%d): %d",
              maxDurationSeconds, sasExpirationDuration));
    }
  }

  /**
   * Validate an azure blob name. Blob name may be a string or null, and must be > 1 char and < 1024
   * chars in length.
   *
   * @param blobName Blob name to validate, or null.
   */
  public static void validateSasBlobName(@Nullable String blobName) {
    if (blobName == null) {
      return;
    }
    if (blobName.isEmpty()) {
      throw new ValidationException("Blob name may not be empty");
    }
    if (blobName.length() > 1024) {
      throw new ValidationException("Blob name must be <= 1024 chars");
    }
  }

  public static void validatePropertiesUpdateRequestBody(List<ApiProperty> properties) {
    if (properties.isEmpty()) {
      throw new ValidationException("Must specify at least one property to update");
    }
  }

  public static void validatePropertiesDeleteRequestBody(List<String> propertyKeys) {
    if (propertyKeys.isEmpty()) {
      throw new ValidationException("Must specify at least one property to delete");
    }
  }
}
