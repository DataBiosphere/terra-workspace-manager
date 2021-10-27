package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.GcsApiConversions;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
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
    FlightMap inputMap = flightContext.getInputParameters();
    ApiGcpGcsBucketCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
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

    StorageCow storageCow = crlService.createStorageCow(projectId);

    // Check whether the bucket exists before attempting to create it. If it does, verify that it
    // is in the current project, which indicates a Stairway retry rather than a real name conflict.
    // Uniqueness within the project is already verified in WSM's DB earlier in this flight.
    try {
      // storageCow.get returns null if the bucket is not found.
      BucketCow existingBucket = storageCow.get(resource.getBucketName());
      if (existingBucket == null) {
        storageCow.create(bucketInfoBuilder.build());
      } else {
        if (!bucketInProject(resource.getBucketName(), projectId)) {
          throw new DuplicateResourceException(
              "The provided bucket name is already in use, please choose another.");
        }
        logger.info("Bucket {} already exists. Continuing.", resource.getBucketName());
      }
    } catch (StorageException storageException) {
      // A 403 on a "get bucket" call or 409 on a "create bucket" call indicates this bucket name is
      // already taken by someone else in GCP's global bucket namespace.
      if (storageException.getCode() == HttpStatus.SC_FORBIDDEN
          || storageException.getCode() == HttpStatus.SC_CONFLICT) {
        throw new DuplicateResourceException(
            "The provided bucket name is already in use, please choose another.");
      }
      // Other cloud errors are unexpected here, rethrow.
      throw storageException;
    }

    return StepResult.getStepResultSuccess();
  }

  /**
   * Assert the provided bucket name exists in the provided project. This uses GCS's auto-generated
   * client library, as the other version does not provide access to a bucket's project.
   *
   * <p>TODO: If GCS's preferred client library supports getting a bucket's project in the future,
   * this implementation should switch.
   */
  private boolean bucketInProject(String bucketName, String projectId) {
    try {
      Storage wsmSaNakedStorageClient = crlService.createWsmSaNakedStorageClient();
      Bucket bucket = wsmSaNakedStorageClient.buckets().get(bucketName).execute();
      BigInteger bucketProjectNumber = bucket.getProjectNumber();
      // Per documentation, Project.getName() will return the int64 generated project number
      // prefixed by the literal "projects/".
      String contextProjectNumber =
          crlService.getCloudResourceManagerCow().projects().get(projectId).execute().getName();
      contextProjectNumber = contextProjectNumber.replaceFirst("^projects/", "");
      return bucketProjectNumber.toString().equals(contextProjectNumber);
    } catch (GoogleJsonResponseException googleEx) {
      // If WSM doesn't have access to this bucket or it isn't found, it is not in the project.
      if (googleEx.getStatusCode() == HttpStatus.SC_FORBIDDEN
          || googleEx.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return false;
      }
      // Other errors from GCP are unexpected and should be rethrown.
      throw new RuntimeException("Error while creating bucket", googleEx);
    } catch (IOException e) {
      // Unexpected error, rethrow.
      throw new RuntimeException("Error while creating bucket", e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    final StorageCow storageCow = crlService.createStorageCow(projectId);
    // WSM should only attempt to delete the buckets it created, so it does nothing if the bucket
    // exists outside the current project. We can guarantee another flight did not create this
    // bucket because uniqueness within the project is already verified in WSM's DB earlier in this
    // flight.
    if (bucketInProject(resource.getBucketName(), projectId)) {
      storageCow.delete(resource.getBucketName());
    }
    return StepResult.getStepResultSuccess();
  }
}
