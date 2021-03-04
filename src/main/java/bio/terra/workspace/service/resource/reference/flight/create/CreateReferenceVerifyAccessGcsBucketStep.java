package bio.terra.workspace.service.resource.reference.flight.create;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.reference.ReferenceGcsBucketResource;
import bio.terra.workspace.service.resource.reference.exception.InvalidReferenceException;
import com.google.cloud.storage.StorageException;

public class CreateReferenceVerifyAccessGcsBucketStep implements Step {
  private final CrlService crlService;

  public CreateReferenceVerifyAccessGcsBucketStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();

    AuthenticatedUserRequest userReq =
        inputMap.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    ReferenceGcsBucketResource referenceResource =
        inputMap.get(JobMapKeys.REQUEST.getKeyName(), ReferenceGcsBucketResource.class);
    String bucketName = referenceResource.getAttributes().getBucketName();

    try {
      // StorageCow.get() returns null if the bucket does not exist or a user does not have access,
      // which fails validation.
      BucketCow bucket = crlService.createStorageCow(userReq).get(bucketName);
      if (bucket == null) {
        throw new InvalidReferenceException(
            String.format(
                "Could not access GCS bucket %s. Ensure the name is correct and that you have access.",
                bucketName));
      }
    } catch (StorageException e) {
      throw new InvalidReferenceException(
          String.format("Error while trying to access GCS bucket %s", bucketName), e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
