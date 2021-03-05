package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.resource.reference.exception.InvalidReferenceException;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A collection of static validation functions */
public class ValidationUtils {
  private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);
  /**
   * Names must be 1-63 characters long, and may consist of alphanumeric characters and underscores
   * (but may not start with an underscore). These restrictions match TDR snapshot name restrictions
   * as we often expect users to use snapshot names as reference names, though this isn't required.
   */
  // TODO: this restriction does not make sense for WSM names in general. I made PF-517 to track.

  public static final Pattern NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][_a-zA-Z0-9]{0,62}$");

  /**
   * GCS bucket name validation is somewhat complex due to rules about usage of "." and restricted
   * names like "goog", but as a baseline they must be 3-222 characters long using lowercase
   * letters, numbers, dashes, underscores, and dots, and must start and end with a letter or
   * number. Matching this pattern is necessary but not sufficient for a valid bucket name.
   */
  public static final Pattern BUCKET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-_.a-z0-9]{1,220}[a-z0-9]$");

  /**
   * BigQuery datasets must be 1-1024 characters, using letters (upper or lowercase), numbers, and
   * underscores.
   */
  public static final Pattern BQ_DATASET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[_a-zA-Z0-9]{1,1024}$");

  public static void validateReferenceName(String name) {
    if (StringUtils.isEmpty(name) || !NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid reference name {}", name);
      throw new InvalidReferenceException(
          "Invalid reference name specified. Name must be 1 to 63 alphanumeric characters or underscores, and cannot start with an underscore.");
    }
  }

  public static void validateBucketName(String name) {
    if (StringUtils.isEmpty(name) || !BUCKET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid bucket name {}", name);
      throw new InvalidReferenceException(
          "Invalid GCS bucket name specified. Names must be 3-222 lowercase letters, numbers, dashes, and underscores. See Google documentation for the full specification.");
    }
  }

  public static void validateBqDatasetName(String name) {
    if (StringUtils.isEmpty(name) || !BQ_DATASET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid BQ name {}", name);
      throw new InvalidReferenceException(
          "Invalid BQ dataset name specified. Name must be 1 to 1024 alphanumeric characters or underscores.");
    }
  }
}
