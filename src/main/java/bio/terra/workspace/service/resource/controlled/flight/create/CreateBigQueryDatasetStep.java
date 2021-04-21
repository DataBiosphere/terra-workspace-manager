package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.DatasetReference;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateBigQueryDatasetStep implements Step {

  private final CrlService crlService;
  private final ControlledBigQueryDatasetResource resource;

  private final Logger logger = LoggerFactory.getLogger(CreateBigQueryDatasetStep.class);

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

    DatasetReference datasetReference = new DatasetReference().setProjectId(resource.getProjectId())
        .setDatasetId(resource.getDatasetName());
    Dataset dataset = new Dataset().setDatasetReference(datasetReference)
        .setLocation(creationParameters.getLocation());
    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    try {
      bqCow.datasets().insert(resource.getProjectId(), dataset).execute();
    } catch (GoogleJsonResponseException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (e.getStatusCode() == HttpStatus.SC_CONFLICT) {
        logger.debug("BQ dataset {} in project {} already exists", resource.getDatasetName(), resource.getProjectId());
        return StepResult.getStepResultSuccess();
      }
      throw new RetryException(e);
    } catch (IOException e) {
      throw new RetryException(e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    try {
      BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
      // With deleteContents set to true, this will delete the dataset even if other steps fail
      // to clean up tables or data.
      bqCow.datasets().delete(resource.getProjectId(), resource.getDatasetName()).setDeleteContents(true).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        logger.debug("BQ dataset {} in project {} already deleted", resource.getDatasetName(), resource.getProjectId());
        return StepResult.getStepResultSuccess();
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }
}
