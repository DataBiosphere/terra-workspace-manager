package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.CreateServiceAccountRequest;
import com.google.api.services.iam.v1.model.ServiceAccount;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Creates a Service Account to be used on the Notebook Instance VM.
 *
 * <p>We create a service account for each notebook instance as a mechanism to control access to the
 * notebook instance via the AI notebooks proxy running in service account mode. Only users with the
 * 'iam.serviceAccounts.actAs' permission on the service account are allowed through the proxy.
 */
public class CreateServiceAccountStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateServiceAccountStep.class);

  private final CrlService crlService;
  private final WorkspaceService workspaceService;
  private final ControlledAiNotebookInstanceResource resource;

  public CreateServiceAccountStep(
      CrlService crlService,
      WorkspaceService workspaceService,
      ControlledAiNotebookInstanceResource resource) {
    this.crlService = crlService;
    this.workspaceService = workspaceService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    IamCow iam = crlService.getIamCow();
    CreateServiceAccountRequest createRequest =
        new CreateServiceAccountRequest()
            .setAccountId(
                flightContext
                    .getWorkingMap()
                    .get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class));
    ServiceAccount serviceAccount;
    try {
      serviceAccount =
          iam.projects().serviceAccounts().create("projects/" + projectId, createRequest).execute();
    } catch (GoogleJsonResponseException e) {
      // If the service account already exists, this step must have run already.
      // Otherwise throw a retry exception.
      if (e.getStatusCode() != HttpStatus.CONFLICT.value()) {
        throw new RetryException(e);
      }
      logger.debug("Service account already created for notebook instance.");
    } catch (IOException e) {
      throw new RetryException(e);
    }
    // TODO(PF-469): Set permissions on service account.
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    IamCow iam = crlService.getIamCow();
    String serviceAccountEmail =
        serviceAccountEmail(
            flightContext.getWorkingMap().get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class),
            projectId);
    try {
      iam.projects().serviceAccounts().delete(serviceAccountEmail).execute();
    } catch (GoogleJsonResponseException e) {
      // The service account may never have been created successfully.
      if (e.getStatusCode() != HttpStatus.NOT_FOUND.value()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /** Returns the service account email based on the project id and the service account id. */
  public static String serviceAccountEmail(String accountId, String projectId) {
    return String.format("%s@%s.iam.gserviceaccount.com", accountId, projectId);
  }
}
