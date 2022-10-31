package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledAzureContainer;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.generated.model.ApiClonedControlledAzureStorageContainer;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureStorageContainer;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CopyAzureStorageContainerDefinitionStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final ControlledAzureStorageContainerResource sourceContainer;
  private final ControlledResourceService controlledResourceService;
  private final CloningInstructions resolvedCloningInstructions;
  private final ResourceDao resourceDao;
  private final LandingZoneApiDispatch landingZoneApiDispatch;

  public CopyAzureStorageContainerDefinitionStep(
      AuthenticatedUserRequest userRequest,
      ResourceDao resourceDao,
      LandingZoneApiDispatch landingZoneApiDispatch,
      ControlledAzureStorageContainerResource sourceContainer,
      ControlledResourceService controlledResourceService,
      CloningInstructions resolvedCloningInstructions) {
    this.resourceDao = resourceDao;
    this.userRequest = userRequest;
    this.sourceContainer = sourceContainer;
    this.controlledResourceService = controlledResourceService;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID);
    String resourceName =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME,
            WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    String description =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_DESCRIPTION,
            WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);
    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var bucketName =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class);
    //    var bucketName =
    //        Optional.ofNullable(foo)
    //            .orElse(
    //                // If the source bucket uses the auto-generated cloud name and the destination
    //                // bucket attempt to do the same, the name will crash as the bucket name must
    // be
    //                // globally unique. Thus, we add cloned- as prefix to the resource name to
    // prevent
    //                // crashing.
    //                ControlledAzureStorageContainerHandler.getHandler()
    //                    .generateCloudName(destinationWorkspaceId, "cloned-" + resourceName));
    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_BUCKET_NAME, bucketName);
    var destinationResourceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);

    final AzureCloudContext azureCloudContext =
        flightContext
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);
    UUID destStorageAccountId;
    try {
      destStorageAccountId = getStorageAccountResourceId(destinationWorkspaceId, azureCloudContext);
    } catch (IllegalStateException e) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new LandingZoneNotFoundException(
              String.format(
                  "Landing zone associated with the Azure cloud context not found. TenantId='%s', SubscriptionId='%s', ResourceGroupId='%s'",
                  azureCloudContext.getAzureTenantId(),
                  azureCloudContext.getAzureSubscriptionId(),
                  azureCloudContext.getAzureResourceGroupId())));
    }
    ControlledAzureStorageContainerResource destinationContainerResource =
        buildDestinationControlledAzureContainer(
            sourceContainer,
            destStorageAccountId,
            destinationWorkspaceId,
            destinationResourceId,
            resourceName,
            description,
            bucketName);

    ApiAzureStorageContainerCreationParameters destinationCreationParameters =
        getDestinationCreationParameters(inputParameters, workingMap);

    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceContainer.getAccessScope());
    ControlledAzureStorageContainerResource clonedContainer =
        controlledResourceService
            .createControlledResourceSync(
                destinationContainerResource, iamRole, userRequest, destinationCreationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedContainer);

    var apiCreatedContainer =
        new ApiCreatedControlledAzureStorageContainer()
            .azureStorageContainer(clonedContainer.toApiResource())
            .resourceId(destinationContainerResource.getResourceId());

    var apiContainerResult =
        new ApiClonedControlledAzureStorageContainer()
            .effectiveCloningInstructions(resolvedCloningInstructions.toApiModel())
            .storageContainer(apiCreatedContainer)
            .sourceWorkspaceId(sourceContainer.getWorkspaceId())
            .sourceResourceId(sourceContainer.getResourceId());

    if (resolvedCloningInstructions.equals(CloningInstructions.COPY_DEFINITION)) {
      FlightUtils.setResponse(flightContext, apiContainerResult, HttpStatus.OK);
    }

    return StepResult.getStepResultSuccess();
  }

  private UUID getStorageAccountResourceId(
      UUID destWorkspaceId, AzureCloudContext azureCloudContext) {
    // attempt to get the storage account from the destination workspace
    var storageAccounts =
        resourceDao.enumerateResources(
            destWorkspaceId, WsmResourceFamily.AZURE_STORAGE_ACCOUNT, null, 0, 100);
    if (storageAccounts.size() == 1) {
      return storageAccounts.get(0).getResourceId();
    }
    if (storageAccounts.size() > 1) {
      throw new RuntimeException("Invalid storage account configuration");
    }

    // if not storage account present in the workspace, sse if there is a landing zone
    // level storage account
    UUID lzId = landingZoneApiDispatch.getLandingZoneId(azureCloudContext);
    Optional<ApiAzureLandingZoneDeployedResource> lzStorageAcct =
        landingZoneApiDispatch.getSharedStorageAccount(
            new BearerToken(userRequest.getRequiredToken()), lzId);
    if (lzStorageAcct.isPresent()) {
      return UUID.fromString(lzStorageAcct.get().getResourceId());
    }

    throw new RuntimeException("Could not find destination storage account");
  }

  private ApiAzureStorageContainerCreationParameters getDestinationCreationParameters(
      FlightMap inputParameters, FlightMap workingMap) {
    return workingMap.get(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS,
        ApiAzureStorageContainerCreationParameters.class);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var clonedContainer =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledGcsBucketResource.class);
    if (clonedContainer != null) {
      controlledResourceService.deleteControlledResourceSync(
          clonedContainer.getWorkspaceId(), clonedContainer.getResourceId(), userRequest);
    }
    return StepResult.getStepResultSuccess();
  }
}
