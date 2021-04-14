package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * Generate and store an account id to use to generate the service account.
 *
 * <p>This is done in a separate step than the service account creation to ensure that at most 1
 * service account is created.
 */
public class GenerateServiceAccountIdStep implements Step {
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // The account id must be 6-30 characters long, and match the regular expression
    // [a-z]([-a-z0-9]*[a-z0-9]) to comply with RFC1035
    // https://cloud.google.com/iam/docs/reference/rest/v1/projects.serviceAccounts/create
    String accountId = "nb-sa-" + RandomStringUtils.randomAlphabetic(20).toLowerCase();
    flightContext.getWorkingMap().put(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, accountId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
