package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import com.azure.core.management.exception.ManagementException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

public abstract class DeleteAzureControlledResourceStep implements DeleteControlledResourceStep {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    try {
      return deleteResource(context);
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      return handleResourceDeleteException(e, context);
    }
  }

  protected abstract StepResult deleteResource(FlightContext context) throws InterruptedException;


  protected static final List<String> missingResourceManagementCodes = Arrays.asList(
      "SubscriptionNotFound",
      "InvalidAuthenticationTokenTenant",
      "AuthorizationFailed"
  );

  /**
   * @param context the flight context, included so downstream implementations can access any parameters they need.
   */
  protected StepResult handleResourceDeleteException(Exception e, FlightContext context) {

    // We had to have had access to create these in the first place
    // so not being able to access them means they are gone or moved
    if (e instanceof ManagementException ex) {
      // FIXME: ex.getValue can fail if the management exception does not have a management error
      if (missingResourceManagementCodes.contains(ex.getValue().getCode())) {

        return StepResult.getStepResultSuccess();
      }
      if (AzureManagementExceptionUtils.getHttpStatus(ex).stream().anyMatch(HttpStatus.NOT_FOUND::equals)) {
        return StepResult.getStepResultSuccess();
      }
      //var statusCode = ex.getResponse().getStatusCode();
      // the 403 can happen if the resource is moved or the subscription is gone
     // if (statusCode == 404 || statusCode == 403) {
       // return StepResult.getStepResultSuccess();
      //}
      //return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(ex), ex);
    }
    // TODO: should this be a fatal failure? (existing seems to me skewed towards STEP_RESULT_FAILURE_RETRY)
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
  }

}
