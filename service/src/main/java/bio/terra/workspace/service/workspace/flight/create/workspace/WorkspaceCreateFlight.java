package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.MakeFlightIdsStep;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.flight.LinkSpendProfilePolicyAttributesStep;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesStep;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;

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
    CloningInstructions cloningInstructions =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
            CloningInstructions.class);
    UUID sourceWorkspaceUuid =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    String projectOwnerGroupId =
        inputParameters.get(WorkspaceFlightMapKeys.PROJECT_OWNER_GROUP_ID, String.class);

    RetryRule serviceRetryRule = RetryRules.shortExponential();
    RetryRule dbRetryRule = RetryRules.shortDatabase();

    addStep(
        new CreateWorkspaceStartStep(workspace, appContext.getWorkspaceDao(), wsmResourceStateRule),
        dbRetryRule);

    if (appContext.getFeatureConfiguration().isTpsEnabled()) {
      addStep(
          new CreateWorkspacePoliciesStep(
              workspace, policyInputs, appContext.getTpsApiDispatch(), userRequest),
          serviceRetryRule);
    }
    // Workspace authz is handled differently depending on whether WSM owns the underlying Sam
    // resource or not, as indicated by the workspace stage enum.
    switch (workspace.getWorkspaceStage()) {
      case MC_WORKSPACE -> {
        if (appContext.getFeatureConfiguration().isTpsEnabled()) {
          addStep(
              new LinkSpendProfilePolicyAttributesStep(
                  workspace.workspaceId(),
                  workspace.spendProfileId(),
                  appContext.getTpsApiDispatch()),
              serviceRetryRule);

          // If we're cloning, we need to copy the policies from the source workspace.
          // This is here instead of in the CloneWorkspaceFlight because we need to do it before
          // we create the workspace in Sam in case there are auth domains.
          // COPY_NOTHING is used when not cloning
          if (cloningInstructions != CloningInstructions.COPY_NOTHING) {
            addStep(
                new MergePolicyAttributesStep(
                    sourceWorkspaceUuid,
                    workspace.workspaceId(),
                    cloningInstructions,
                    appContext.getTpsApiDispatch()),
                serviceRetryRule);
          }
        }
        addStep(
            new CreateWorkspaceAuthzStep(
                workspace,
                appContext.getSamService(),
                appContext.getTpsApiDispatch(),
                appContext.getFeatureConfiguration(),
                userRequest,
                projectOwnerGroupId),
            serviceRetryRule);
      }
      case RAWLS_WORKSPACE ->
          addStep(
              new CheckSamWorkspaceAuthzStep(workspace, appContext.getSamService(), userRequest),
              serviceRetryRule);
      default ->
          throw new InternalLogicException(
              "Unknown workspace stage during creation: " + workspace.getWorkspaceStage().name());
    }
    addStep(
        new CreateWorkspaceFinishStep(workspace.workspaceId(), appContext.getWorkspaceDao()),
        dbRetryRule);

    if (applicationIds != null) {
      addStep(
          new MakeFlightIdsStep(
              List.of(EnableApplicationsStep.FLIGHT_ID_KEY), WorkspaceFlightMapKeys.FLIGHT_IDS));
      addStep(
          new EnableApplicationsStep(
              applicationIds, appContext.getApplicationService(), userRequest, workspace),
          dbRetryRule);
    }
  }
}
