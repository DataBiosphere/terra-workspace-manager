package bio.terra.workspace.service.resource.controlled.cloud.azure.flight;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_WITHOUT_REGION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.WORKSPACE_ID_TO_AZURE_CLOUD_CONTEXT_MAP;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Retrieves the azure cloud context of the Terra workspace where the resources with missing regions
 * are in. The azure cloud context are deserialized and used in {@link
 * RetrieveAzureResourcesRegionStep}.
 */
public class RetrieveAzureCloudContexts implements Step {

  final AzureCloudContextService azureCloudContextService;

  public RetrieveAzureCloudContexts(AzureCloudContextService azureCloudContextService) {
    this.azureCloudContextService = azureCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(context.getWorkingMap(), CONTROLLED_RESOURCES_WITHOUT_REGION);
    List<ControlledResource> controlledResources =
        context.getWorkingMap().get(CONTROLLED_RESOURCES_WITHOUT_REGION, new TypeReference<>() {});
    Set<UUID> workspaceIds =
        controlledResources != null
            ? controlledResources.stream()
                .map(WsmResource::getWorkspaceId)
                .collect(Collectors.toSet())
            : Collections.emptySet();
    Map<UUID, String> workspaceIdToAzureCloudContext = new HashMap<>();
    for (var workspaceId : workspaceIds) {
      String serializedCloudContext =
          azureCloudContextService.getRequiredAzureCloudContext(workspaceId).serialize();
      workspaceIdToAzureCloudContext.put(workspaceId, serializedCloudContext);
    }
    context
        .getWorkingMap()
        .put(WORKSPACE_ID_TO_AZURE_CLOUD_CONTEXT_MAP, workspaceIdToAzureCloudContext);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
