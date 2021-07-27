package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import static bio.terra.workspace.common.utils.GcpUtils.getControlPlaneProjectId;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.bigquery.datatransfer.v1.CreateTransferConfigRequest;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.StartManualTransferRunsRequest;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfig;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

public class CreateDatasetTransferConfigStep implements Step {
  private static final Duration SCHEDULE_DELAY = Duration.ofMinutes(1);
  private final ControlledBigQueryDatasetResource sourceDataset;

  public CreateDatasetTransferConfigStep(ControlledBigQueryDatasetResource sourceDataset) {
    this.sourceDataset = sourceDataset;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String controlPlaneProjectId = getControlPlaneProjectId();
    flightContext
        .getWorkingMap()
        .put(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, controlPlaneProjectId);

    final DatasetCloneInputs sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, DatasetCloneInputs.class);
    final DatasetCloneInputs destinationInputs =
        workingMap.get(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, DatasetCloneInputs.class);
    final String location =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ControlledResourceKeys.LOCATION,
            ControlledResourceKeys.LOCATION,
            String.class);

    final String transferConfigName =
        buildConfigName(controlPlaneProjectId, location, flightContext.getFlightId());

    try {
      final DataTransferServiceClient dataTransferServiceClient =
          DataTransferServiceClient.create();
      final Map<String, Value> params = new HashMap<>();
      params.put(
          "source_project_id",
          Value.newBuilder().setStringValue(sourceInputs.getProjectId()).build());
      params.put(
          "source_dataset_id",
          Value.newBuilder().setStringValue(sourceInputs.getDatasetName()).build());

      TransferConfig inputTransferConfig =
          TransferConfig.newBuilder()
              .setDestinationDatasetId(destinationInputs.getDatasetName())
              .setName(transferConfigName)
              .setDisplayName("Dataset Clone")
              .setParams(Struct.newBuilder().putAllFields(params).build())
              //              .setSchedule(buildSchedule())
              .setName(flightContext.getFlightId())
              .build();
      // TODO: check if the transfer already exists
      CreateTransferConfigRequest request =
          CreateTransferConfigRequest.newBuilder()
              .setParent(destinationInputs.getProjectId())
              .setTransferConfig(inputTransferConfig)
              .build();
      TransferConfig createdConfig = dataTransferServiceClient.createTransferConfig(request);
      StartManualTransferRunsRequest manualTransferRunsRequest =
          StartManualTransferRunsRequest.newBuilder().setParent(createdConfig.getName()).build();

      dataTransferServiceClient.startManualTransferRuns(manualTransferRunsRequest);

    } catch (IOException | RuntimeException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private String buildConfigName(String projectName, String location, String configId) {
    return String.format(
        "projects/%s/locations/%s/transferConfigs/%s", projectName, location, configId);
  }

  private String buildSchedule() {
    final OffsetDateTime scheduledTime = OffsetDateTime.now(ZoneOffset.UTC).plus(SCHEDULE_DELAY);
    return String.format(
        "%d %d %d %d *",
        scheduledTime.getMinute(),
        scheduledTime.getHour(),
        scheduledTime.getDayOfMonth(),
        scheduledTime.getMonthValue());
  }
}
