package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

/** A {@link Flight} for deleting a Google cloud context for a workspace. */
// TODO(PF-555): There is a race condition if this flight runs at the same time as new controlled
//  resource creation, which may leak resources in Sam. Workspace locking would solve this issue.
public class DeleteGcpContextFlight extends Flight {
  private static final CloudPlatform CLOUD_PLATFORM = CloudPlatform.GCP;

  public DeleteGcpContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    UUID workspaceId =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    RetryRule retryRule = RetryRules.cloudLongRunning();

    // We delete controlled resources from Sam and WSM databases, but do not need to delete the
    // actual cloud objects, as GCP handles the cleanup when we delete the containing project.
    addStep(
        new DeleteControlledSamResourcesStep(
            appContext.getSamService(),
            appContext.getResourceDao(),
            workspaceId,
            CLOUD_PLATFORM,
            userRequest),
        retryRule);
    addStep(
        new DeleteControlledDbResourcesStep(
            appContext.getResourceDao(), workspaceId, CLOUD_PLATFORM),
        retryRule);
    addStep(
        new DeleteProjectStep(appContext.getCrlService(), appContext.getWorkspaceDao()), retryRule);
    addStep(new DeleteGcpContextStep(appContext.getWorkspaceDao(), workspaceId), retryRule);
  }
}
