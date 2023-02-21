package bio.terra.workspace.service.workspace.flight.gcp;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.workspace.flight.DeleteControlledDbResourcesStep;
import bio.terra.workspace.service.workspace.flight.DeleteControlledSamResourcesStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
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
    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));

    RetryRule retryRule = RetryRules.cloudLongRunning();

    // We delete controlled resources from Sam and WSM databases, but do not need to delete the
    // actual cloud objects, as GCP handles the cleanup when we delete the containing project.
    addStep(
        new DeleteControlledSamResourcesStep(
            appContext.getSamService(), appContext.getResourceDao(), workspaceUuid, CLOUD_PLATFORM),
        retryRule);
    addStep(
        new DeleteControlledDbResourcesStep(
            appContext.getResourceDao(), workspaceUuid, CLOUD_PLATFORM),
        retryRule);
    addStep(
        new DeleteGcpProjectStep(
            appContext.getCrlService(), appContext.getGcpCloudContextService()),
        retryRule);
    addStep(
        new DeleteGcpContextStep(appContext.getGcpCloudContextService(), workspaceUuid), retryRule);
  }
}
