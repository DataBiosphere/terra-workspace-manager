package bio.terra.workspace.service.resource.controlled.cloud.aws;

import java.time.Duration;

/** Constants shared among resource types. */
public class AwsResourceConstants {

  /** Default region of a resource. */
  public static final String DEFAULT_REGION = "us-east-1";

  /** Minimum and maximum duration in seconds for AWS credentials */
  public static final int MIN_CREDENTIAL_DURATION_SECONDS = 900;

  public static final int MAX_CREDENTIAL_DURATION_SECONDS = 3600;

  /** Maximum length of a S3 storage folder name */
  public static final int MAX_S3_STORAGE_FOLDER_NAME_LENGTH = 1024;

  /** Maximum length of a SageMaker notebook instance name */
  public static final int MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH = 63;

  /**
   * SageMaker client waiter timeout duration waitUntilInService: AWS does not specify an upper
   * limit for notebook launch times, Pending->InService typically takes 5-10 minutes Edge cases
   * observed: 45+ minutes waitUntilStopped: Stopping->Stopped typically takes 3-5 minutes
   * waitUntilDeleted: Deleting->[Deleted] typically takes 2-4 minutes
   */
  public static final Duration SAGEMAKER_WAITER_TIMEOUT = Duration.ofMinutes(30);
}
