package bio.terra.workspace.service.folder.flights;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FOLDER_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WORKSPACE_ID;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import java.util.UUID;

public class FolderDeleteFlight extends Flight {

  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public FolderDeleteFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    UUID workspaceUuid =
        UUID.fromString(FlightUtils.getRequired(inputParameters, WORKSPACE_ID, String.class));
    UUID folderId = FlightUtils.getRequired(inputParameters, FOLDER_ID, UUID.class);

    RetryRule databaseRetry = RetryRules.shortDatabase();
    addStep(
        new FindResourcesInFolderStep(
            workspaceUuid, folderId, flightBeanBag.getFolderDao(), flightBeanBag.getResourceDao()),
        databaseRetry);

    addStep(new CreateFlightIdStep());

    addStep(new LaunchControlledResourcesDeletionFlightStep(folderId), RetryRules.cloud());

    addStep(
        new DeleteReferencedResourcesStep(flightBeanBag.getResourceDao(), workspaceUuid),
        databaseRetry);

    addStep(new AwaitControlledResourcesDeletionFlightStep(), RetryRules.cloudLongRunning());

    addStep(
        new DeleteFoldersStep(flightBeanBag.getFolderDao(), workspaceUuid, folderId),
        databaseRetry);
  }
}
