package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;

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
import com.google.api.services.iam.v1.model.CreateServiceAccountRequest;
import com.google.api.services.iam.v1.model.ServiceAccount;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Creates a Service Account to be used on the Notebook Instance VM. Deletes the service account on
 * undo.
 *
 * <p>We create a service account for each notebook instance as a mechanism to control access to the
 * notebook instance via the AI notebooks proxy running in service account mode. Only users with the
 * 'iam.serviceAccounts.actAs' permission on the service account are allowed through the proxy.
 *
 * <p>{@see
 * https://cloud.google.com/ai-platform/notebooks/docs/troubleshooting#opening_a_notebook_results_in_a_403_forbidden_error}
 */
public class CreateServiceAccountStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateServiceAccountStep.class);

  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;
  private final ControlledAiNotebookInstanceResource resource;

  public CreateServiceAccountStep(
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService,
      ControlledAiNotebookInstanceResource resource) {
    this.crlService = crlService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    IamCow iam = crlService.getIamCow();
    String serviceAccountId =
        flightContext.getWorkingMap().get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class);
    CreateServiceAccountRequest createRequest =
        new CreateServiceAccountRequest()
            .setAccountId(serviceAccountId)
            .setServiceAccount(
                new ServiceAccount()
                    // Set a description to help with debugging.
                    .setDescription(
                        String.format(
                            "SA for AI Notebook Instance id %s in location %s",
                            resource.getInstanceId(), resource.getLocation())));
    try {
      iam.projects().serviceAccounts().create("projects/" + projectId, createRequest).execute();
    } catch (GoogleJsonResponseException e) {
      // If the service account already exists, this step must have run already.
      // Otherwise throw a retry exception.
      if (e.getStatusCode() != HttpStatus.CONFLICT.value()) {
        throw new RetryException(e);
      }
      String serviceAccountEmail =
          ServiceAccountName.emailFromAccountId(serviceAccountId, projectId);
      logger.debug(
          "Service account {} already created for notebook instance.", serviceAccountEmail);
    } catch (IOException e) {
      throw new RetryException(e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    IamCow iam = crlService.getIamCow();
    String serviceAccountEmail =
        ServiceAccountName.emailFromAccountId(
            flightContext.getWorkingMap().get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class),
            projectId);
    try {
      iam.projects()
          .serviceAccounts()
          .delete("projects/" + projectId + "/serviceAccounts/" + serviceAccountEmail)
          .execute();
    } catch (GoogleJsonResponseException e) {
      // The service account may never have been created successfully or have already been deleted.
      if (e.getStatusCode() != HttpStatus.NOT_FOUND.value()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
      logger.debug("No service account {} found to be deleted.", serviceAccountEmail);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
