package bio.terra.workspace.service.resource.controlled.flight.update;

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
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.cloud.storage.BucketInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateGcsBucketStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(UpdateGcsBucketStep.class);
  private final ControlledGcsBucketResource bucketResource;
  private final CrlService crlService;
  private final WorkspaceService workspaceService;

  public UpdateGcsBucketStep(
      ControlledGcsBucketResource bucketResource,
      CrlService crlService,
      WorkspaceService workspaceService) {
    this.bucketResource = bucketResource;
    this.crlService = crlService;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputMap = flightContext.getInputParameters();
    final ApiGcpGcsBucketCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
    final String projectId =
        workspaceService.getRequiredGcpProject(bucketResource.getWorkspaceId());
    final BucketInfo bucketInfo =
        BucketInfo.newBuilder(bucketResource.getBucketName())
            .setLocation(creationParameters.getLocation())
            .setStorageClass(
                GcsApiConversions.toGcsApi(creationParameters.getDefaultStorageClass()))
            .setLifecycleRules(GcsApiConversions.toGcsApi(creationParameters.getLifecycle()))
            .build();

    final StorageCow storageCow = crlService.createStorageCow(projectId);

    final BucketCow existingBucketCow = storageCow.get(bucketInfo.getName());
    if (existingBucketCow == null) {
      throw new ResourceNotFoundException(
          String.format("Cannot find GCS bucket %s to update", bucketInfo.getName()));
    }
//    final BucketCow updatedBucketCow =
        existingBucketCow.toBuilder()
            .setLifecycleRules(bucketInfo.getLifecycleRules())
            .setStorageClass(bucketInfo.getStorageClass())
            .build()
            .update();

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
