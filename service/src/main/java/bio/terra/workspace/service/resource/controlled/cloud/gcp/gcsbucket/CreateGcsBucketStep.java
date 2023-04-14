package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateGcsBucketStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateGcsBucketStep.class);
  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final GcpCloudContextService gcpCloudContextService;

  public CreateGcsBucketStep(
      CrlService crlService,
      ControlledGcsBucketResource resource,
      GcpCloudContextService gcpCloudContextService) {
    this.crlService = crlService;
    this.resource = resource;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters, ControlledResourceKeys.CREATION_PARAMETERS);
    ApiGcpGcsBucketCreationParameters creationParameters =
        inputParameters.get(
            ControlledResourceKeys.CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());

    BucketInfo.Builder bucketInfoBuilder =
        BucketInfo.newBuilder(resource.getBucketName())
            .setLocation(creationParameters.getLocation());

    // Remaining creation parameters are optional
    Optional.ofNullable(creationParameters.getDefaultStorageClass())
        .map(GcsApiConversions::toGcsApi)
        .ifPresent(bucketInfoBuilder::setStorageClass);

    bucketInfoBuilder.setLifecycleRules(
        Optional.ofNullable(creationParameters.getLifecycle())
            .map(GcsApiConversions::toGcsApiRulesList)
            .orElse(Collections.emptyList()));

    BucketInfo.IamConfiguration iamConfiguration =
        BucketInfo.IamConfiguration.newBuilder().setIsUniformBucketLevelAccessEnabled(true).build();
    bucketInfoBuilder.setIamConfiguration(iamConfiguration);

    // Check whether the bucket exists before attempting to create it. If it does, verify that it
    // is in the current project, which indicates a Stairway retry rather than a real name conflict.
    // Uniqueness within the project is already verified in WSM's DB earlier in this flight.
    try {
      Optional<Bucket> existingBucket = getBucket(resource.getBucketName());
      if (existingBucket.isEmpty()) {
        StorageCow storageCow = crlService.createStorageCow(projectId);
        storageCow.create(bucketInfoBuilder.build());
      } else if (bucketInProject(existingBucket.get(), projectId)) {
        logger.info(
            "Bucket {} already exists in workspace project, this is a Stairway retry. Continuing.",
            resource.getBucketName());
      } else {
        throw new DuplicateResourceException(
            "The provided bucket name is already in use, please choose another.");
      }
    } catch (StorageException storageException) {
      // A 409 on a "create bucket" call indicates this bucket name is already taken by someone else
      // in GCP's global bucket namespace, even if we don't have permission to GET it.
      if (storageException.getCode() == HttpStatus.SC_CONFLICT) {
        throw new DuplicateResourceException(
            "The provided bucket name is already in use, please choose another.", storageException);
      }
      if (storageException.getCode() == HttpStatus.SC_BAD_REQUEST) {
        throw new BadRequestException(
            "Received 400 BAD_REQUEST exception when creating a new gcs-bucket", storageException);
      }
      // Other cloud errors are unexpected here, rethrow.
      throw storageException;
    }

    return StepResult.getStepResultSuccess();
  }

  /**
   * Try to fetch an existing bucket with the provided name. Because buckets are globally namespaced
   * a bucket may exist but be outside of the current project, where it may or may not be accessible
   * to WSM. There is no guarantee that the returned bucket exists in the workspace project.
   */
  private Optional<Bucket> getBucket(String bucketName) {
    try {
      Storage wsmSaNakedStorageClient = crlService.createWsmSaNakedStorageClient();
      return Optional.of(wsmSaNakedStorageClient.buckets().get(bucketName).execute());
    } catch (GoogleJsonResponseException googleEx) {
      // If WSM doesn't have access to this bucket or it isn't found, return empty.
      if (googleEx.getStatusCode() == HttpStatus.SC_FORBIDDEN
          || googleEx.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return Optional.empty();
      }
      // Other errors from GCP are unexpected and should be rethrown.
      throw new RuntimeException("Error while looking up existing bucket project", googleEx);
    } catch (IOException e) {
      // Unexpected error, rethrow.
      throw new RuntimeException("Error while looking up existing bucket project", e);
    }
  }

  /**
   * Return whether the provided bucket name exists in the provided project or not. This uses GCS's
   * auto-generated client library, as the other version does not provide access to a bucket's
   * project.
   *
   * <p>TODO: If GCS's preferred client library supports getting a bucket's project in the future,
   * this implementation should switch.
   *
   * @return True if the given bucket exists in the given project, false otherwise.
   */
  private boolean bucketInProject(Bucket bucket, String projectId) {
    try {
      BigInteger bucketProjectNumber = bucket.getProjectNumber();
      // Per documentation, Project.getName() will return the int64 generated project number
      // prefixed by the literal "projects/".
      String contextProjectNumber =
          crlService.getCloudResourceManagerCow().projects().get(projectId).execute().getName();
      contextProjectNumber = contextProjectNumber.replaceFirst("^projects/", "");
      logger.info(
          "Bucket {} exists in project {}. Workspace project is {}",
          bucket.getName(),
          bucketProjectNumber.toString(),
          contextProjectNumber);
      return bucketProjectNumber.toString().equals(contextProjectNumber);
    } catch (IOException e) {
      // Unexpected error from looking up provided project ID, rethrow.
      throw new RuntimeException("Error while looking up existing project", e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    // WSM should only attempt to delete the buckets it created, so it does nothing if the bucket
    // exists outside the current project. We can guarantee another flight did not create this
    // bucket because uniqueness within the project is already verified in WSM's DB earlier in this
    // flight.
    Optional<Bucket> existingBucket = getBucket(resource.getBucketName());
    if (existingBucket.isPresent() && bucketInProject(existingBucket.get(), projectId)) {
      final StorageCow storageCow = crlService.createStorageCow(projectId);
      storageCow.delete(resource.getBucketName());
    }
    return StepResult.getStepResultSuccess();
  }
}
