package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.BqApiConversions;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.bigquery.model.Dataset;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetrieveBigQueryDatasetCloudAttributesStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RetrieveBigQueryDatasetCloudAttributesStep.class);
  private final ControlledBigQueryDatasetResource datasetResource;
  private final CrlService crlService;
  private final WorkspaceService workspaceService;

  public RetrieveBigQueryDatasetCloudAttributesStep(
      ControlledBigQueryDatasetResource datasetResource,
      CrlService crlService,
      WorkspaceService workspaceService) {
    this.datasetResource = datasetResource;
    this.crlService = crlService;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String projectId =
        workspaceService.getRequiredGcpProject(datasetResource.getWorkspaceId());
    final String datasetId = datasetResource.getDatasetName();
    final BigQueryCow bigQueryCow = crlService.createWsmSaBigQueryCow();

    // get the existing dataset
    Dataset existingDataset;
    try {
      existingDataset = bigQueryCow.datasets().get(projectId, datasetId).execute();
      if (existingDataset == null) {
        logger.info("No dataset found with id {}.", datasetId);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, null);
      }
    } catch (IOException ioEx) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ioEx);
    }

    // save the current attributes in the working map
    final ApiGcpBigQueryDatasetUpdateParameters existingUpdateParameters =
        BqApiConversions.toUpdateParameters(existingDataset);
    workingMap.put(ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS, existingUpdateParameters);

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
