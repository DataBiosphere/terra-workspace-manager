package bio.terra.workspace.service.resource;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** A collection of static validation functions specific to GCP */
@Component
public class GcpResourceValidationUtils {
  private static final Logger logger = LoggerFactory.getLogger(GcpResourceValidationUtils.class);

  // GCS Bucket

  private static final String GOOG_PREFIX = "goog";

  private static final ImmutableList<String> GOOGLE_NAMES = ImmutableList.of("google", "g00gle");

  /**
   * GCS bucket name validation is somewhat complex due to rules about usage of "." and restricted
   * names like "goog", but as a baseline they must be 3-222 characters long using lowercase
   * letters, numbers, dashes, underscores, and dots, and must start and end with a letter or
   * number. Matching this pattern is necessary but not sufficient for a valid bucket name.
   */
  private static final Pattern GCS_REFERENCED_BUCKET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-_.a-z0-9]{1,220}[a-z0-9]$");

  /**
   * Similar to REFERENCED_BUCKET_NAME_VALIDATION_PATTERN, except prohibits underscores. GCP
   * recommends against underscores in bucket names because DNS hostnames can't have underscores. In
   * particular, Nextflow fails if bucket name has underscore (because bucket name isn't valid DNS
   * hostname.)
   */
  private static final Pattern GCS_CONTROLLED_BUCKET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-.a-z0-9]{1,220}[a-z0-9]$");

  private static final String GCS_REFERENCED_BUCKET_NAME_VALIDATION_FAILURE_ERROR =
      "Invalid GCS bucket name specified. Names must be 3-222 lowercase letters, numbers, dashes, and underscores. See Google documentation for the full specification.";

  private static final String GCS_CONTROLLED_BUCKET_NAME_VALIDATION_FAILURE_ERROR =
      "Invalid GCS bucket name specified. Names must be 3-222 lowercase letters, numbers, and dashes. See Google documentation for the full specification.";

  public static void validateGcsBucketNameDisallowUnderscore(String name) {
    validateGcsBucketName(
        name,
        GCS_CONTROLLED_BUCKET_NAME_VALIDATION_PATTERN,
        GCS_CONTROLLED_BUCKET_NAME_VALIDATION_FAILURE_ERROR);
  }

  public static void validateGcsBucketNameAllowsUnderscore(String name) {
    validateGcsBucketName(
        name,
        GCS_REFERENCED_BUCKET_NAME_VALIDATION_PATTERN,
        GCS_REFERENCED_BUCKET_NAME_VALIDATION_FAILURE_ERROR);
  }

  /**
   * Validates gcs-bucket name following Google documentation
   * https://cloud.google.com/storage/docs/naming-buckets#requirements on a best-effort base.
   *
   * <p>This method DOES NOT guarantee that the bucket name is valid.
   *
   * @param name gcs-bucket name
   * @param validationFailureError validationFailureError
   * @throws InvalidNameException throws exception when the bucket name fails to conform to the
   *     Google naming convention for bucket name.
   */
  @VisibleForTesting
  public static void validateGcsBucketName(
      String name, Pattern validationPattern, String validationFailureError) {
    if (StringUtils.isEmpty(name) || !validationPattern.matcher(name).matches()) {
      logger.warn("Invalid bucket name {}", name);
      throw new InvalidNameException(validationFailureError);
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
      if (name.contains(google)) {
        logger.warn("Invalid bucket name {}", name);
        throw new InvalidNameException(
            "Invalid GCS bucket name specified. Bucket names cannot contains google or mis-spelled google. See Google documentation https://cloud.google.com/storage/docs/naming-buckets#requirements for the full specification.");
      }
    }
  }

  // GCS Object

  /**
   * Magic prefix for ACME HTTP challenge.
   *
   * <p>See https://tools.ietf.org/html/draft-ietf-acme-acme-09#section-8.3
   */
  private static final String ACME_CHALLENGE_PREFIX = ".well-known/acme-challenge/";

  // An object named "." or ".." is nearly impossible for a user to delete.
  private static final ImmutableList<String> DISALLOWED_OBJECT_NAMES = ImmutableList.of(".", "..");

  /**
   * Validate GCS object name.
   *
   * @param objectName full path to the object in the bucket
   * @throws InvalidNameException InvalidNameException
   */
  public static void validateGcsObjectName(String objectName) {
    int nameLength = objectName.getBytes(StandardCharsets.UTF_8).length;
    if (nameLength < 1 || nameLength > 1024) {
      throw new InvalidNameException(
          "bucket object names must contain any sequence of valid Unicode characters, of length 1-1024 bytes when UTF-8 encoded");
    }
    if (objectName.startsWith(ACME_CHALLENGE_PREFIX)) {
      throw new InvalidNameException(
          "bucket object name cannot start with .well-known/acme-challenge/");
    }
    for (String disallowedObjectName : DISALLOWED_OBJECT_NAMES) {
      if (disallowedObjectName.equals(objectName)) {
        throw new InvalidNameException("bucket object name cannot be . or ..");
      }
    }
  }

  // BQ DataSet

  /**
   * BigQuery datasets must be 1-1024 characters, using letters (upper or lowercase), numbers, and
   * underscores.
   */
  private static final Pattern GCP_BQ_DATASET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[_a-zA-Z0-9]{1,1024}$");

  public static void validateBqDatasetName(String name) {
    if (StringUtils.isEmpty(name)
        || !GCP_BQ_DATASET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid BQ name {}", name);
      throw new InvalidReferenceException(
          "Invalid BQ dataset name specified. Name must be 1 to 1024 alphanumeric characters or underscores.");
    }
  }

  // BQ DataTable

  /**
   * BigQuery data table name must be 1-1024 characters, contains Unicode characters in category L
   * (letter), M (mark), N (number), Pc (connector, including underscore), Pd (dash), Zs (space).
   */
  private static final Pattern BQ_DATATABLE_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[\\p{L}\\p{N}\\p{Pc}\\p{Pd}\\p{Zs}\\p{M}]{1,1024}$");

  public static void validateBqDataTableName(String name) {
    if (StringUtils.isEmpty(name)
        || !BQ_DATATABLE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid data table name {}", name);
      throw new InvalidNameException(
          "Invalid BQ table name specified. Name must be 1-1024 characters, contains Unicode characters in category L"
              + " (letter), M (mark), N (number), Pc (connector, including underscore), Pd (dash), Zs (space)");
    }
  }

  // AI Notebook

  /**
   * AI Notebook instances must be 1-63 characters, using lower case letters, numbers, and dashes.
   * The first character must be a lower case letter, and the last character must not be a dash.
   */
  private static final Pattern AI_NOTEBOOK_INSTANCE_NAME_VALIDATION_PATTERN =
      Pattern.compile("(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)");

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

  // GCE instance

  /**
   * Compute instances must be 1-63 characters, using lower case letters, numbers, and dashes. The
   * first character must be a lower case letter, and the last character must not be a dash.
   */
  public static final Pattern GCE_INSTANCE_NAME_VALIDATION_PATTERN =
      Pattern.compile("(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)");

  public static void validateGceInstanceId(String name) {
    if (!GCE_INSTANCE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid GCE instance ID {}", name);
      throw new InvalidReferenceException(
          "Invalid GCE instance ID specified. ID must be 1 to 63 alphanumeric lower case characters or dashes, where the first character is a lower case letter.");
    }
  }

  /**
   * Dataproc cluster names must be 1-52 characters, using lower case letters, numbers, and dashes.
   * The first character must be a lower case letter, and the last character must not be a dash.
   * See:
   * https://cloud.google.com/dataproc/docs/guides/create-cluster#creating_a_cloud_dataproc_cluster.
   */
  public static final Pattern DATAPROC_CLUSTER_NAME_VALIDATION_PATTERN =
      Pattern.compile("(?:[a-z](?:[-a-z0-9]{0,50}[a-z0-9])?)");

  public static void validateDataprocClusterId(String name) {
    if (!DATAPROC_CLUSTER_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid Dataproc cluster ID {}", name);
      throw new InvalidReferenceException(
          "Invalid Dataproc cluster ID specified. ID must be 1 to 52 alphanumeric lower case characters or dashes, where the first character is a lower case letter and the last character is not a dash.");
    }
  }
}
