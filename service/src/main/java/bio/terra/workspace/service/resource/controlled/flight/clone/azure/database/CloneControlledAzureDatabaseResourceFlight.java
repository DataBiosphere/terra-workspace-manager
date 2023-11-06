package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.CreateAzureStorageContainerStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloneControlledAzureDatabaseResourceFlight extends Flight {

  private static final Logger logger =
      LoggerFactory.getLogger(CloneControlledAzureDatabaseResourceFlight.class);

  public CloneControlledAzureDatabaseResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    logger.info(
        "(sanity check) CloneControlledAzureDatabaseResourceFlight constructor has been called");

    logger.info("applicationContext {}", applicationContext);

    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
        WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
        WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    UUID sourceWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    UUID destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    String sourceDbName =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_SOURCE_DATABASE_NAME, String.class);
    String dbServerName =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_SOURCE_DATABASE_SERVER,
            String.class);
    String dbUserName =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_USER, String.class);
    String blobContainerUrlAuthenticated =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_BLOB_CONTAINER_URL_AUTHENTICATED,
            String.class);

    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

    // Flight Plan
    // 1. TODO
    // 2. ...

    String targetDbName = sourceDbName + "clone";
    String blobFileName = sourceDbName + ".dump";
    String storageContainerName = sourceDbName + "-private-storage";  // TODO: are there problems with this name?

//    addStep(
//        new CreateAzureStorageContainerStep(
//            flightBeanBag.getAzureConfig(),
//            flightBeanBag.getCrlService(),
//            ControlledAzureStorageContainerResource.builder()
//                .storageContainerName(storageContainerName)
//                .common(
//                    ControlledResourceFields.builder()
//                        .
//                        .build()
//                )
//                .build()
//        ));

    addStep(
        new DumpAzureDatabaseStep(
            inputParameters.get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class),
            flightBeanBag.getAzureDatabaseUtilsRunner(),
            sourceWorkspaceId,
            destinationWorkspaceId,
            sourceDbName,
            dbServerName,
            dbUserName,
            blobFileName,
            blobContainerUrlAuthenticated),
        RetryRules.shortDatabase()); // TODO: what kind of retry rule should be used?

    addStep(
        new CreateAzureDatabaseStep(
            inputParameters.get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class),
            flightBeanBag.getAzureDatabaseUtilsRunner(),
            sourceWorkspaceId,
            targetDbName),
        RetryRules.shortDatabase());

    addStep(
        new RestoreAzureDatabaseStep(
            inputParameters.get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class),
            flightBeanBag.getAzureDatabaseUtilsRunner(),
            sourceWorkspaceId,
            destinationWorkspaceId,
            targetDbName,
            dbServerName,
            dbUserName,
            blobFileName,
            blobContainerUrlAuthenticated),
        RetryRules.shortDatabase()); // TODO: what kind of retry rule should be used?
  }
}
