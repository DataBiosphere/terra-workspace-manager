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
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.ProjectName;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfig;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfigName;
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
        TransferConfigName.ofProjectLocationTransferConfigName(
                controlPlaneProjectId, location, flightContext.getFlightId())
            .toString();

    try (DataTransferServiceClient dataTransferServiceClient = DataTransferServiceClient.create()) {
      final Map<String, Value> params = new HashMap<>();
      params.put(
          "source_project_id",
          Value.newBuilder()
              .setStringValue(ProjectName.of(sourceInputs.getProjectId()).toString())
              .build());
      params.put(
          "source_dataset_id",
          Value.newBuilder().setStringValue(sourceInputs.getDatasetId()).build());
      final TransferConfig inputTransferConfig =
          TransferConfig.newBuilder()
              .setDestinationDatasetId(destinationInputs.getDatasetId())
              .setName(transferConfigName)
              .setDisplayName("Dataset Clone")
              .setParams(Struct.newBuilder().putAllFields(params).build())
              //              .setDataSourceId("cross_region_copy")
              //              .setSchedule(buildSchedule())
              .build();
      final TransferConfig createdConfig =
          dataTransferServiceClient.createTransferConfig(
              ProjectName.of(destinationInputs.getProjectId()).toString(), inputTransferConfig);
      final String dataSourceId = createdConfig.getDataSourceId(); // FIXME
      //      final StartManualTransferRunsRequest manualTransferRunsRequest =
      //
      // StartManualTransferRunsRequest.newBuilder().setParent(createdConfig.getName()).build();
      //
      //      final StartManualTransferRunsResponse response =
      //          dataTransferServiceClient.startManualTransferRuns(manualTransferRunsRequest);
      //      final List<TransferRun> runs = response.getRunsList();
      //      final int runsCount = response.getRunsCount();
    } catch (IOException | RuntimeException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
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
