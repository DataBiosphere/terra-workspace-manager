package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled BigQuery dataset resource. This will delete a dataset even if it
 * has tables or data present.
 */
public class DeleteBigQueryDatasetStep implements Step {

  private final ControlledBigQueryDatasetResource resource;
  private final CrlService crlService;

  private final Logger logger = LoggerFactory.getLogger(DeleteBigQueryDatasetStep.class);

  public DeleteBigQueryDatasetStep(
      ControlledBigQueryDatasetResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    try {
      // With deleteContents set to true, this will delete the dataset even if it still has tables.
      bqCow
          .datasets()
          .delete(resource.getProjectId(), resource.getDatasetName())
          .setDeleteContents(true)
          .execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        logger.info(
            "BQ dataset {} in project {} already deleted",
            resource.getDatasetName(),
            resource.getProjectId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Deletes cannot be undone, so we log a warning and continue the flight.
    logger.warn(
        "Cannot undo delete of BQ dataset {} in project {}. Continuing flight.",
        resource.getDatasetName(),
        resource.getProjectId());
    return StepResult.getStepResultSuccess();
  }
}
