package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

public class WorkspaceCreateFlight extends Flight {

  public WorkspaceCreateFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    WsmResourceStateRule wsmResourceStateRule = appContext.getFeatureConfiguration().getStateRule();

    // get data from inputs that steps need
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    Workspace workspace =
        FlightUtils.getRequired(inputParameters, JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    TpsPolicyInputs policyInputs =
        inputParameters.get(WorkspaceFlightMapKeys.POLICIES, TpsPolicyInputs.class);
    List<String> applicationIds =
        inputParameters.get(WorkspaceFlightMapKeys.APPLICATION_IDS, new TypeReference<>() {});

    RetryRule serviceRetryRule = RetryRules.shortExponential();
    RetryRule dbRetryRule = RetryRules.shortDatabase();

    addStep(
        new CreateWorkspaceStartStep(
            workspace, appContext.getWorkspaceDao(), wsmResourceStateRule, applicationIds),
        dbRetryRule);

    // Workspace authz is handled differently depending on whether WSM owns the underlying Sam
    // resource or not, as indicated by the workspace stage enum.
    switch (workspace.getWorkspaceStage()) {
      case MC_WORKSPACE -> {
        if (appContext.getFeatureConfiguration().isTpsEnabled()) {
          addStep(
              new CreateWorkspacePoliciesStep(
                  workspace, policyInputs, appContext.getTpsApiDispatch(), userRequest),
              serviceRetryRule);
        }
        addStep(
            new CreateWorkspaceAuthzStep(
                workspace,
                appContext.getSamService(),
                appContext.getTpsApiDispatch(),
                appContext.getFeatureConfiguration(),
                userRequest),
            serviceRetryRule);
      }
      case RAWLS_WORKSPACE -> addStep(
          new CheckSamWorkspaceAuthzStep(workspace, appContext.getSamService(), userRequest),
          serviceRetryRule);
      default -> throw new InternalLogicException(
          "Unknown workspace stage during creation: " + workspace.getWorkspaceStage().name());
    }
    addStep(
        new CreateWorkspaceFinishStep(workspace.workspaceId(), appContext.getWorkspaceDao()),
        dbRetryRule);
  }
}
