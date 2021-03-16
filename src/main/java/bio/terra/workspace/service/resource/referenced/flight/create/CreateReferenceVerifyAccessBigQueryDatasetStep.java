package bio.terra.workspace.service.resource.referenced.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;

public class CreateReferenceVerifyAccessBigQueryDatasetStep implements Step {
  private final CrlService crlService;

  public CreateReferenceVerifyAccessBigQueryDatasetStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();

    AuthenticatedUserRequest userReq =
        inputMap.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    ReferencedBigQueryDatasetResource referenceResource =
        inputMap.get(JobMapKeys.REQUEST.getKeyName(), ReferencedBigQueryDatasetResource.class);
    String projectId = referenceResource.getProjectId();
    String datasetName = referenceResource.getDatasetName();

    if (!crlService.bigQueryDatasetExists(projectId, datasetName, userReq)) {
      throw new InvalidReferenceException(
          String.format(
              "Could not access BigQuery dataset %s in project %s. Ensure the name and GCP project are correct and that you have access.",
              datasetName, projectId));
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
