package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.Optional;
import java.util.UUID;

/** Top-most flight for cloning a workspace. Launches sub-flights for most of the work. */
public class CloneWorkspaceFlight extends Flight {

  public CloneWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // NOTE: MergePolicyAttributesStep is not part of this flight,
    // it happens during workspace creation so auth domains can be added to workspace

    // Flight Plan
    // * Clone all folders in the workspace
    // * Build a list of resources to clone and attach the updated cloned folder id
    // * Create job IDs for future sub-flights and a couple other things
    // * Launch a flight to create a cloud context if necessary
    // * Await the context flight
    // * Launch a flight to clone all resources on the list
    // * Await the clone all resources flight and build a response

    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    var cloudRetryRule = RetryRules.cloud();
    var longCloudRetryRule = RetryRules.cloudLongRunning();
    var userRequest =
        FlightUtils.getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    var sourceWorkspaceId =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID,
            UUID.class);
    var spendProfile =
        inputParameters.get(WorkspaceFlightMapKeys.SPEND_PROFILE, SpendProfile.class);

    addStep(
        new CloneAllFoldersStep(flightBeanBag.getSamService(), flightBeanBag.getFolderDao()),
        RetryRules.shortDatabase());

    addStep(new FindResourcesToCloneStep(flightBeanBag.getResourceDao()), cloudRetryRule);

    addStep(new CreateIdsForFutureStepsStep());

    // Only create a cloud context if the source workspace has a cloud context
    Optional<GcpCloudContext> gcpCloudContextOptional =
        flightBeanBag.getGcpCloudContextService().getGcpCloudContext(sourceWorkspaceId);
    if (gcpCloudContextOptional.isPresent()) {
      SpendProfile gcpSpendProfile =
          getSpendProfile(spendProfile, gcpCloudContextOptional.get(), flightBeanBag, userRequest);

      addStep(
          new LaunchCreateCloudContextFlightStep(
              flightBeanBag.getWorkspaceService(),
              CloudPlatform.GCP,
              gcpSpendProfile,
              ControlledResourceKeys.CREATE_GCP_CLOUD_CONTEXT_FLIGHT_ID),
          cloudRetryRule);
      addStep(
          new AwaitCreateCloudContextFlightStep(
              ControlledResourceKeys.CREATE_GCP_CLOUD_CONTEXT_FLIGHT_ID),
          longCloudRetryRule);
    }

    Optional<AzureCloudContext> azureCloudContextOptional =
        flightBeanBag.getAzureCloudContextService().getAzureCloudContext(sourceWorkspaceId);
    if (azureCloudContextOptional.isPresent()) {
      SpendProfile azureSpendProfile =
          getSpendProfile(
              spendProfile, azureCloudContextOptional.get(), flightBeanBag, userRequest);
      addStep(
          new LaunchCreateCloudContextFlightStep(
              flightBeanBag.getWorkspaceService(),
              CloudPlatform.AZURE,
              azureSpendProfile,
              ControlledResourceKeys.CREATE_AZURE_CLOUD_CONTEXT_FLIGHT_ID),
          cloudRetryRule);
      addStep(
          new AwaitCreateCloudContextFlightStep(
              ControlledResourceKeys.CREATE_AZURE_CLOUD_CONTEXT_FLIGHT_ID),
          cloudRetryRule);
    }

    addStep(new LaunchCloneAllResourcesFlightStep(), cloudRetryRule);
    addStep(new AwaitCloneAllResourcesFlightStep(), longCloudRetryRule);
  }

  /*
   * There is an API mismatch in the V1 APIs. If a new spend profile is passed with
   * the clone workspace, then we use it to create all the new cloud contexts,
   * even though a spend profile is only valid for one cloud. Since we don't have
   * multi-cloud workspaces right now, we will not have a mismatch of spend profile
   * and cloud context.
   */
  private SpendProfile getSpendProfile(
      SpendProfile spendProfile,
      CloudContext cloudContext,
      FlightBeanBag flightBeanBag,
      AuthenticatedUserRequest userRequest) {
    if (spendProfile != null) {
      return spendProfile;
    }

    var spendProfileService = flightBeanBag.getSpendProfileService();
    var features = flightBeanBag.getFeatureConfiguration();
    return spendProfileService.authorizeLinking(
        cloudContext.getCommonFields().spendProfileId(), features.isBpmGcpEnabled(), userRequest);
  }
}
