package bio.terra.workspace.service.resource.controlled.flight.delete.notebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.DELETE_NOTEBOOK_SERVICE_ACCOUNT_EMAIL;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting the service account created for a controlled AI Platform Notebook instance.
 *
 * <p>Note that this should be done only after the instance using the service account is deleted.
 */
public class DeleteServiceAccountStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteAiNotebookInstanceStep.class);
  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;

  public DeleteServiceAccountStep(
      ControlledAiNotebookInstanceResource resource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService) {
    this.resource = resource;
    this.crlService = crlService;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    IamCow iam = crlService.getIamCow();
    ServiceAccountName serviceAccountName =
        ServiceAccountName.builder()
            .projectId(projectId)
            .email(
                flightContext
                    .getWorkingMap()
                    .get(DELETE_NOTEBOOK_SERVICE_ACCOUNT_EMAIL, String.class))
            .build();
    try {
      iam.projects().serviceAccounts().delete(serviceAccountName).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        logger.info(
            "Service account {} for Notebook instance already deleted.",
            serviceAccountName.email());
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
    String serviceAccountEmail =
        flightContext.getWorkingMap().get(DELETE_NOTEBOOK_SERVICE_ACCOUNT_EMAIL, String.class);
    logger.error(
        "Cannot undo delete of GCS AI Platform Notebook service account {} in workspace {} for resource {}.",
        serviceAccountEmail,
        resource.getWorkspaceId(),
        resource.getResourceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
