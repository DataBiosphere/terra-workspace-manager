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
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.cloud.storage.BucketInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateGcsBucketStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateGcsBucketStep.class);
  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final WorkspaceService workspaceService;

  public CreateGcsBucketStep(
      CrlService crlService,
      ControlledGcsBucketResource resource,
      WorkspaceService workspaceService) {
    this.crlService = crlService;
    this.resource = resource;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    ApiGcpGcsBucketCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    BucketInfo bucketInfo =
        BucketInfo.newBuilder(resource.getBucketName())
            .setLocation(creationParameters.getLocation())
            .setStorageClass(
                GcsApiConversions.toGcsApi(creationParameters.getDefaultStorageClass()))
            .setLifecycleRules(
                GcsApiConversions.toGcsApiRulesList(creationParameters.getLifecycle()))
            .build();

    StorageCow storageCow = crlService.createStorageCow(projectId);

    // Don't try to create it if it already exists. At this point the assumption is
    // this is a redo and this step created it already.
    BucketCow existingBucket = storageCow.get(resource.getBucketName());
    if (existingBucket == null) {
      storageCow.create(bucketInfo);
    } else {
      logger.info("Bucket {} already exists. Continuing.", resource.getBucketName());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    final StorageCow storageCow = crlService.createStorageCow(projectId);
    storageCow.delete(resource.getBucketName());
    return StepResult.getStepResultSuccess();
  }
}
