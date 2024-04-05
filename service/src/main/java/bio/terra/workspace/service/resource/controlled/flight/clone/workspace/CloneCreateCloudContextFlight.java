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

/** Flight for creating a cloud context for a cloned workspace */
public class CloneCreateCloudContextFlight extends Flight {
  public CloneCreateCloudContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    // Flight Plan
    // * Launch a flight to create a cloud context if necessary
    // * Await the context flight
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
            inputParameters, ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    var spendProfile =
        inputParameters.get(WorkspaceFlightMapKeys.SPEND_PROFILE, SpendProfile.class);

    addStep(new CreateCloudContextIdsForFutureStepsStep());

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
        // Note that `isBpmGcpEnabled` is a misnomer: it is not specific to GCP.
        // This flag should be removed, as we also run with BPM enabled at this point.
        cloudContext.getCommonFields().spendProfileId(), features.isBpmGcpEnabled(), userRequest);
  }
}
