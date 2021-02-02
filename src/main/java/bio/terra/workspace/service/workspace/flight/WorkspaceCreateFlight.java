package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import org.jetbrains.annotations.NotNull;

public class WorkspaceCreateFlight extends Flight {

  public WorkspaceCreateFlight(
      @NotNull FlightMap inputParameters, @NotNull Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    // get data from inputs that steps need
    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    addStep(new CreateWorkspaceAuthzStep(appContext.getSamService(), userReq));
    addStep(new CreateWorkspaceStep(appContext.getWorkspaceDao()));
  }
}
