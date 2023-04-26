package bio.terra.workspace.service.resource.controlled.cloud.aws;

/** Constants shared among resource types. */
public class AwsResourceConstants {

  /** Default region of a resource. */
  public static final String DEFAULT_REGION = "us-east-1";

  /** Minimum and maximum duration in seconds for AWS credentials */
  public static final int MIN_CREDENTIAL_DURATION_SECONDS = 900;

  public static final int MAX_CREDENTIAL_DURATION_SECONDS = 3600;

  /** Maximum length of a S3 storage folder name */
  public static final int MAX_S3_STORAGE_FOLDER_NAME_LENGTH = 1024;
}
