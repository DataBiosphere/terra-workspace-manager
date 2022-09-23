package bio.terra.workspace.service.folder.flights;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FOLDER_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WORKSPACE_ID;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ReferencedResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FolderDeleteFlight extends DeleteControlledResourceFlight {

  /**
   * @inheritdoc
   */
  public FolderDeleteFlight(FlightMap inputParameters, Object beanBag) throws InterruptedException {
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
        new DeleteFoldersStep(flightBeanBag.getFolderDao(), workspaceUuid, folderId),
        databaseRetry);
  }
}
