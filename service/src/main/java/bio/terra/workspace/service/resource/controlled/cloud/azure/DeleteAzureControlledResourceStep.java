package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import com.azure.core.management.exception.ManagementException;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Common base class for deleting Azure resources. Wraps the Azure resource deletion logic with
 * handling for common exceptions.
 */
public abstract class DeleteAzureControlledResourceStep implements DeleteControlledResourceStep {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    try {
      return deleteResource(context);
      // explicitly exclude InterruptedException from the handling below
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      return handleResourceDeleteException(e, context);
    }
  }

  /**
   * Method that should preform the actual resource deletion. The logic here is wrapped with the
   * common exception handling for Azure resources provided in `handleResourceDeleteException`.
   */
  protected abstract StepResult deleteResource(FlightContext context) throws InterruptedException;

  protected static final List<String> missingResourceManagementCodes =
      Arrays.asList(
          "SubscriptionNotFound", "InvalidAuthenticationTokenTenant", "AuthorizationFailed");

  /**
   * Provides default exception handling for deleting Azure resources. Handles common error codes
   * that happen when resources or the underlying resources (such as the MRG or subscription) are
   * missing or previously deleted. Specific steps may override this to provide their own or
   * additional exception handling.
   *
   * @param context the flight context, included so downstream implementations can access any
   *     parameters they need.
   */
  protected StepResult handleResourceDeleteException(Exception e, FlightContext context) {

    if (e instanceof ManagementException ex) {
      // the 403 can happen if the resource is moved or the subscription is gone
      // We had to have had access to create these in the first place
      // so not being able to access them means they are gone or moved,
      // or something else has happened out of band that is out of the realm of what we can
      // realistically support
      if (AzureManagementExceptionUtils.getHttpStatus(ex).stream()
          .anyMatch(
              status ->
                  HttpStatus.NOT_FOUND.equals(status) || HttpStatus.FORBIDDEN.equals(status))) {
        return StepResult.getStepResultSuccess();
      }

      if (ex.getValue() != null
          && missingResourceManagementCodes.contains(ex.getValue().getCode())) {
        return StepResult.getStepResultSuccess();
      }

      // retry any other non-4xx errors
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(ex), ex);
    }
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
  }
}
