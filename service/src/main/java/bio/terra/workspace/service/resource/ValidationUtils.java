package bio.terra.workspace.service.resource;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of static validation functions
 */
public class ValidationUtils {

  private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);

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

  /**
   * BigQuery data table name must be 1-1024 characters, contains Unicode characters in category L
   * (letter), M (mark), N (number), Pc (connector, including underscore), Pd (dash), Zs (space).
   */
  public static final Pattern BQ_DATATABLE_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[\\p{L}\\p{N}\\p{Pc}\\p{Pd}\\p{Zs}\\p{M}]{1,1024}$");

  /**
   * AI Notebook instances must be 1-63 characters, using lower case letters, numbers, and dashes.
   * The first character must be a lower case letter, and the last character must not be a dash.
   */
  public static final Pattern AI_NOTEBOOK_INSTANCE_NAME_VALIDATION_PATTERN =
      Pattern.compile("(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)");

  /**
   * Resource names must be 1-1024 characters, using letters, numbers, dashes, and underscores and
   * must not start with a dash or underscore.
   */
  public static final Pattern RESOURCE_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][-_a-zA-Z0-9]{0,1023}$");

  // An object named "." or ".." is nearly impossible for a user to delete.
  private static final ImmutableList<String> DISALLOWED_OBJECT_NAMES = ImmutableList.of(".", "..");

  /**
   * Magic prefix for ACME HTTP challenge.
   *
   * <p>See https://tools.ietf.org/html/draft-ietf-acme-acme-09#section-8.3
   */
  public static final String ACME_CHALLENGE_PREFIX = ".well-known/acme-challenge/";

  private static final String GOOG_PREFIX = "goog";
  private static final ImmutableList<String> GOOGLE_NAMES = ImmutableList.of("google", "g00gle");
  private static final int MAX_RESOURCE_DESCRIPTION_NAME = 2048;

  /**
   * Validates gcs-bucket name following Google documentation https://cloud.google.com/storage/docs/naming-buckets#requirements
   * on a best-effort base.
   *
   * <p>This method DOES NOT guarentee that the bucket name is valid.
   *
   * @param name gcs-bucket name
   * @throws InvalidNameException throws exception when the bucket name fails to conform to the
   * Google naming convention for bucket name.
   */
  public static void validateBucketName(String name) {
    if (StringUtils.isEmpty(name) || !BUCKET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid bucket name {}", name);
      throw new InvalidNameException(
          "Invalid GCS bucket name specified. Names must be 3-222 lowercase letters, numbers, dashes, and underscores. See Google documentation for the full specification.");
    }
    for (String s : name.split("\\.")) {
      if (s.length() > 63) {
        logger.warn("Invalid bucket name {}", name);
        throw new InvalidNameException(
            "Invalid GCS bucket name specified. Names containing dots can contain up to 222 characters, but each dot-separated component can be no longer than 63 characters. See Google documentation https://cloud.google.com/storage/docs/naming-buckets#requirements for the full specification.");
      }
    }
    if (name.startsWith(GOOG_PREFIX)) {
      logger.warn("Invalid bucket name {}", name);
      throw new InvalidNameException(
          "Invalid GCS bucket name specified. Bucket names cannot have prefix goog. See Google documentation https://cloud.google.com/storage/docs/naming-buckets#requirements for the full specification.");
    }
    for (String google : GOOGLE_NAMES) {
      logger.warn("Invalid bucket name {}", name);
      if (name.contains(google)) {
        throw new InvalidNameException(
            "Invalid GCS bucket name specified. Bucket names cannot contains google or mis-spelled google. See Google documentation https://cloud.google.com/storage/docs/naming-buckets#requirements for the full specification.");
      }
    }
  }

  public static void validateBucketFileName(String fileName) {
    int nameLength = fileName.getBytes(StandardCharsets.UTF_8).length;
    if (nameLength < 1 || nameLength > 1024) {
      throw new InvalidNameException(
          "bucket file names must contain any sequence of valid Unicode characters, of length 1-1024 bytes when UTF-8 encoded"
      );
    }
    if (fileName.startsWith(ACME_CHALLENGE_PREFIX)) {
      throw new InvalidNameException("bucket file name cannot start with .well-known/acme-challenge/");
    }
    for (String disallowedObjectName : DISALLOWED_OBJECT_NAMES) {
      if (disallowedObjectName.equals(fileName)) {
        throw new InvalidNameException("bucket file name cannot be . or ..");
      }
    }
  }

  public static void validateBqDatasetName(String name) {
    if (StringUtils.isEmpty(name) || !BQ_DATASET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid BQ name {}", name);
      throw new InvalidReferenceException(
          "Invalid BQ dataset name specified. Name must be 1 to 1024 alphanumeric characters or underscores.");
    }
  }

  public static void validateBqDataTableName(String name) {
    if (StringUtils.isEmpty(name)
        || !BQ_DATATABLE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid data table name %s", name);
      throw new InvalidNameException(
          "Invalid BQ table name specified. Name must be 1-1024 characters, contains Unicode characters in category L"
              + " (letter), M (mark), N (number), Pc (connector, including underscore), Pd (dash), Zs (space)");
    }
  }

  public static void validateAiNotebookInstanceId(String name) {
    if (!AI_NOTEBOOK_INSTANCE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid AI Notebook instance ID {}", name);
      throw new InvalidReferenceException(
          "Invalid AI Notebook instance ID specified. ID must be 1 to 63 alphanumeric lower case characters or dashes, where the first character is a lower case letter.");
    }
  }

  public static void validate(ApiGcpAiNotebookInstanceCreationParameters creationParameters) {
    validateAiNotebookInstanceId(creationParameters.getInstanceId());
    // OpenApi one-of fields aren't being generated correctly, so we do manual one-of fields.
    if ((creationParameters.getVmImage() == null)
        == (creationParameters.getContainerImage() == null)) {
      throw new InconsistentFieldsException(
          "Exactly one of vmImage or containerImage must be specified.");
    }
    validate(creationParameters.getVmImage());
  }

  private static void validate(ApiGcpAiNotebookInstanceVmImage vmImage) {
    if (vmImage == null) {
      return;
    }
    if ((vmImage.getImageName() == null) == (vmImage.getImageFamily() == null)) {
      throw new InconsistentFieldsException(
          "Exactly one of imageName or imageFamily must be specified for a valid vmImage.");
    }
  }

  public static void validateResourceName(String name) {
    if (StringUtils.isEmpty(name) || !RESOURCE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid resource name {}", name);
      throw new InvalidNameException(
          "Invalid resource name specified. Name must be 1 to 1024 alphanumeric characters, underscores, and dashes and must not start with a dash or underscore.");
    }
  }

  public static void validateResourceDescriptionName(@Nullable String name) {
    if (name != null && name.length() > MAX_RESOURCE_DESCRIPTION_NAME) {
      throw new InvalidNameException(
          "Invalid description specified. Description must be under 2048 characters.");
    }
  }
}
