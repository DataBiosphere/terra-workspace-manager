package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import com.google.cloud.bigquery.DatasetInfo;

public class CreateBigQueryDatasetStep implements Step {

  private final CrlService crlService;
  private final ControlledBigQueryDatasetResource resource;

  public CreateBigQueryDatasetStep(
      CrlService crlService, ControlledBigQueryDatasetResource resource) {
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, ApiGcpBigQueryDatasetCreationParameters.class);

    DatasetInfo datasetInfo =
        DatasetInfo.newBuilder(resource.getDatasetName())
            .setLocation(creationParameters.getLocation())
            .build();

    BigQueryCow bqCow = crlService.createBigQueryCow(resource.getProjectId());
    bqCow.create(datasetInfo);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    BigQueryCow bqCow = crlService.createBigQueryCow(resource.getProjectId());
    bqCow.delete(resource.getDatasetName());
    return StepResult.getStepResultSuccess();
  }
}
