package bio.terra.workspace.service.resource.controlled.cloud.aws.flight;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_WITHOUT_REGION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.WORKSPACE_ID_TO_AWS_CLOUD_CONTEXT_MAP;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Retrieves the aws cloud context of the Terra workspace where the resources with missing regions
 * are in. The aws cloud context are deserialized and used in {@link
 * RetrieveAwsResourcesRegionStep}.
 */
public class RetrieveAwsCloudContexts implements Step {

  final AwsCloudContextService awsCloudContextService;

  public RetrieveAwsCloudContexts(AwsCloudContextService awsCloudContextService) {
    this.awsCloudContextService = awsCloudContextService;
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
    Map<UUID, String> workspaceIdToAwsCloudContext = new HashMap<>();
    for (var workspaceId : workspaceIds) {
      String serializedCloudContext =
          awsCloudContextService.getRequiredAwsCloudContext(workspaceId).serialize();
      workspaceIdToAwsCloudContext.put(workspaceId, serializedCloudContext);
    }
    context
        .getWorkingMap()
        .put(WORKSPACE_ID_TO_AWS_CLOUD_CONTEXT_MAP, workspaceIdToAwsCloudContext);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
