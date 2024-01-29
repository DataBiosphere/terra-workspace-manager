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
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

public class CreateWorkspaceV2Flight extends Flight {

  public CreateWorkspaceV2Flight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    WsmResourceStateRule wsmResourceStateRule = appContext.getFeatureConfiguration().getStateRule();

    // get data from inputs that steps need
    AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    Workspace workspace =
        FlightUtils.getRequired(inputParameters, JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    TpsPolicyInputs policyInputs =
        inputParameters.get(WorkspaceFlightMapKeys.POLICIES, TpsPolicyInputs.class);
    List<String> applicationIds =
        inputParameters.get(WorkspaceFlightMapKeys.APPLICATION_IDS, new TypeReference<>() {});
    CloudPlatform cloudPlatform =
        inputParameters.get(WorkspaceFlightMapKeys.CLOUD_PLATFORM, CloudPlatform.class);
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

    // If we have a cloud context to create, add the step to run that flight
    if (cloudPlatform != null) {
      SpendProfile spendProfile =
          FlightUtils.getRequired(
              inputParameters, WorkspaceFlightMapKeys.SPEND_PROFILE, SpendProfile.class);
      String flightId =
          FlightUtils.getRequired(
              inputParameters, WorkspaceFlightMapKeys.CREATE_CLOUD_CONTEXT_FLIGHT_ID, String.class);

      addStep(
          new RunCreateCloudContextFlightStep(
              appContext.getWorkspaceService(),
              workspace,
              cloudPlatform,
              spendProfile,
              flightId,
              userRequest),
          serviceRetryRule);
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
