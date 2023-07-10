package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteMetadataStartStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteSamResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;

/**
 * This class is used for deleting GCP resources during cloud context delete. For those resources,
 * we do not delete the cloud resource itself. We just delete the Sam resource and the WSM metadata.
 * As a separate step, we delete the GCP project which deletes all resources within it. The flight
 * has the same interface (input parameters) as the regular DeleteControlledResourcesFlight, but
 * requires exactly one resource.
 */
public class DeleteCloudContextResourceFlight extends Flight {

  public DeleteCloudContextResourceFlight(FlightMap inputParameters, Object beanBag)
      throws InterruptedException {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    UUID workspaceUuid =
        UUID.fromString(
            FlightUtils.getRequired(
                inputParameters, WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    UUID resourceId =
        UUID.fromString(
            FlightUtils.getRequired(
                inputParameters, WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, String.class));

    RetryRule cloudRetry = RetryRules.cloud();
    RetryRule dbRetry = RetryRules.shortDatabase();

    // Mark the resource as in the deleting step to avoid concurrency issues.
    addStep(
        new DeleteMetadataStartStep(flightBeanBag.getResourceDao(), workspaceUuid, resourceId),
        dbRetry);

    // Delete the Sam resource. That will make the object inaccessible.
    addStep(
        new DeleteSamResourceStep(
            flightBeanBag.getResourceDao(),
            flightBeanBag.getSamService(),
            workspaceUuid,
            resourceId),
        cloudRetry);

    // Delete the metadata
    addStep(
        new DeleteMetadataStep(
            flightBeanBag.getResourceDao(),
            workspaceUuid,
            resourceId,
            flightBeanBag.getWorkspaceActivityLogService()),
        dbRetry);
  }
}
