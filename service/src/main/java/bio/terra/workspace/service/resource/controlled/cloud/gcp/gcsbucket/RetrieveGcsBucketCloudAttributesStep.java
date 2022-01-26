package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.storage.BucketInfo;
import javax.ws.rs.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetrieveGcsBucketCloudAttributesStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RetrieveGcsBucketCloudAttributesStep.class);
  private final ControlledGcsBucketResource bucketResource;
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;
  private final RetrievalMode retrievalMode;

  // TODO: PF-850 - just use creation parameters and remove retrieval mode.
  public enum RetrievalMode {
    UPDATE_PARAMETERS,
    CREATION_PARAMETERS
  }

  public RetrieveGcsBucketCloudAttributesStep(
      ControlledGcsBucketResource bucketResource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService,
      RetrievalMode retrievalMode) {
    this.bucketResource = bucketResource;
    this.crlService = crlService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.retrievalMode = retrievalMode;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String projectId =
        gcpCloudContextService.getRequiredGcpProject(bucketResource.getWorkspaceId());
    // get the storage cow
    final StorageCow storageCow = crlService.createStorageCow(projectId);

    // get the existing bucket cow
    final BucketCow existingBucketCow = storageCow.get(bucketResource.getBucketName());
    if (existingBucketCow == null) {
      logger.error(
          "Can't construct COW for pre-existing bucket {}", bucketResource.getBucketName());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, null);
    }

    // get the attributes
    final BucketInfo existingBucketInfo = existingBucketCow.getBucketInfo();
    switch (retrievalMode) {
      case UPDATE_PARAMETERS:
        final ApiGcpGcsBucketUpdateParameters existingUpdateParameters =
            GcsApiConversions.toUpdateParameters(existingBucketInfo);
        workingMap.put(ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS, existingUpdateParameters);
        break;
      case CREATION_PARAMETERS:
        final ApiGcpGcsBucketCreationParameters creationParameters =
            GcsApiConversions.toCreationParameters(existingBucketInfo);
        workingMap.put(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
        break;
      default:
        throw new BadRequestException(
            String.format("Unsupported Retrieval mode %s", retrievalMode.toString()));
    }

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
