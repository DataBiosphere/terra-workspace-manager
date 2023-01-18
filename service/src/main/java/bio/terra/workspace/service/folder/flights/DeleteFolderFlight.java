package bio.terra.workspace.service.folder.flights;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FOLDER_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WORKSPACE_ID;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import java.util.UUID;

/** A flight to delete folder and its sub-folders, along with all the resources in it. */
public class DeleteFolderFlight extends DeleteControlledResourcesFlight {

  /**
   * @inheritdoc
   */
  public DeleteFolderFlight(FlightMap inputParameters, Object beanBag) throws InterruptedException {
    // Steps are added in the super class `DeleteControlledResourcesFlight` to delete
    // controlled resources.
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    UUID workspaceUuid =
        UUID.fromString(FlightUtils.getRequired(inputParameters, WORKSPACE_ID, String.class));
    UUID folderId = FlightUtils.getRequired(inputParameters, FOLDER_ID, UUID.class);
    RetryRule databaseRetry = RetryRules.shortDatabase();
    addStep(
        new DeleteReferencedResourcesStep(flightBeanBag.getResourceDao(), workspaceUuid),
        databaseRetry);

    addStep(
        new DeleteFolderRecursiveStep(flightBeanBag.getFolderDao(), workspaceUuid, folderId),
        databaseRetry);
  }
}
