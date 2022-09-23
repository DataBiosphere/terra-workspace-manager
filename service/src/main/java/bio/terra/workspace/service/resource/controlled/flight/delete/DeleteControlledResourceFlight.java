package bio.terra.workspace.service.resource.controlled.flight.delete;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys.RESOURCE;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.flight.create.GetCloudContextStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;

/**
 * Flight for type-agnostic deletion of a controlled resource. All type-specific information should
 * live in individual steps.
 */
public class DeleteControlledResourceFlight extends Flight {

  @Override
  public void addStep(Step step, RetryRule retryRule) {
    super.addStep(step, retryRule);
  }

  public DeleteControlledResourceFlight(FlightMap inputParameters, Object beanBag)
      throws InterruptedException {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    final UUID workspaceUuid =
        UUID.fromString(
            FlightUtils.getRequired(
                inputParameters, WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    ControlledResource resource =
        inputParameters.get(RESOURCE, ControlledResource.class);
    List<WsmResource> controlledResources = inputParameters.get(
        ControlledResourceKeys.RESOURCES_TO_DELETE, new TypeReference<>() {});
    if (resource != null) {
      addStep(flightBeanBag, resource, workspaceUuid);
    } else if (controlledResources != null) {
      for (WsmResource controlledResource: controlledResources) {
          addStep(flightBeanBag, (ControlledResource) controlledResource, workspaceUuid);
      }
    }
  }

  public void addStep(FlightBeanBag flightBeanBag, ControlledResource resource, UUID workspaceUuid) {
    final RetryRule cloudRetry = RetryRules.cloud();

    // Get the cloud context for the resource we are deleting
    addStep(
        new GetCloudContextStep(
            workspaceUuid,
            resource.getResourceType().getCloudPlatform(),
            flightBeanBag.getGcpCloudContextService(),
            flightBeanBag.getAzureCloudContextService()),
        cloudRetry);

    // Delete the cloud resource. This has unique logic for each resource type. Depending on the
    // specifics of the resource type, this step may require the flight to run asynchronously.
    resource.addDeleteSteps(this, flightBeanBag);

    // Delete the Sam resource. That will make the object inaccessible.
    addStep(
        new DeleteSamResourceStep(
            flightBeanBag.getResourceDao(),
            flightBeanBag.getSamService(),
            workspaceUuid,
            resource.getResourceId()), cloudRetry);

    // Delete the metadata
    addStep(
        new DeleteMetadataStep(
            flightBeanBag.getResourceDao(), workspaceUuid, resource.getResourceId()),
        RetryRules.shortDatabase());
  }
}
