package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.bigquery.datatransfer.v1.CreateTransferConfigRequest;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.ProjectName;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfig;
import com.google.cloud.bigquery.datatransfer.v1.TransferRun;
import com.google.cloud.bigquery.datatransfer.v1.TransferState;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyBigQueryDatasetDifferentRegionStep implements Step {
  public static final Logger logger =
      LoggerFactory.getLogger(CopyBigQueryDatasetDifferentRegionStep.class);

  private final SamService samService;
  private final ControlledBigQueryDatasetResource sourceDataset;
  private final AuthenticatedUserRequest userRequest;
  private final GcpCloudContextService gcpCloudContextService;

  public CopyBigQueryDatasetDifferentRegionStep(
      SamService samService,
      ControlledBigQueryDatasetResource sourceDataset,
      AuthenticatedUserRequest userRequest,
      GcpCloudContextService gcpCloudContextService) {
    this.samService = samService;
    this.sourceDataset = sourceDataset;
    this.userRequest = userRequest;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters, ControlledResourceKeys.DESTINATION_DATASET_NAME);

    UUID destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    String destinationProjectId =
        gcpCloudContextService.getRequiredGcpProject(destinationWorkspaceId);

    String destinationDatasetId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_DATASET_NAME, String.class);

    Map<String, Value> params = new HashMap<>();
    String sourceDatasetId = sourceDataset.getDatasetName();
    String sourceProjectId = sourceDataset.getProjectId();
    params.put("source_project_id", Value.newBuilder().setStringValue(sourceProjectId).build());
    params.put("source_dataset_id", Value.newBuilder().setStringValue(sourceDatasetId).build());

    TransferConfig transferConfig =
        TransferConfig.newBuilder()
            .setDestinationDatasetId(destinationDatasetId)
            .setDisplayName(destinationDatasetId)
            .setDataSourceId("cross_region_copy")
            .setParams(Struct.newBuilder().putAllFields(params).build())
            .build();

    try (DataTransferServiceClient dataTransferServiceClient = DataTransferServiceClient.create()) {
      ProjectName parent = ProjectName.of(destinationProjectId);
      String petSaEmail =
          SamRethrow.onInterrupted(
              () ->
                  samService.getOrCreatePetSaEmail(
                      gcpCloudContextService.getRequiredGcpProject(destinationWorkspaceId),
                      userRequest.getRequiredToken()),
              "enablePet");

      CreateTransferConfigRequest request =
          CreateTransferConfigRequest.newBuilder()
              .setParent(parent.toString())
              .setTransferConfig(transferConfig)
              .setServiceAccountName(petSaEmail)
              .build();

      TransferConfig config = dataTransferServiceClient.createTransferConfig(request);
      DataTransferServiceClient.ListTransferRunsPagedResponse runs =
          dataTransferServiceClient.listTransferRuns(config.getName());

      for (TransferRun run : runs.iterateAll()) {
        final String currentRunName = run.getName();
        // Wait for job to complete
        int sleepTimeSeconds = 1;
        while (true) {
          final TransferRun currentRun = dataTransferServiceClient.getTransferRun(currentRunName);
          final TransferState runState = currentRun.getState();

          logger.debug("Run {} is {}", currentRunName, runState);
          // Data transfer run has finished. It is either SUCCEEDED (4), FAILED (5), or CANCELLED
          // (6)
          if (currentRun.getStateValue() >= 4) {
            if (!"SUCCEEDED".equals(runState.toString())) {
              final String errorMessage = currentRun.getErrorStatus().getMessage();
              logger.warn("Job {} failed: {}", currentRunName, errorMessage);
              return new StepResult(
                  StepStatus.STEP_RESULT_FAILURE_FATAL, new RuntimeException(errorMessage));
            }
            break;
          }
          TimeUnit.SECONDS.sleep(sleepTimeSeconds);
          sleepTimeSeconds = Math.min(2 * sleepTimeSeconds, 60);
        }
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here because the whole dataset will be deleted in the undo path for
  // an earlier step.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
