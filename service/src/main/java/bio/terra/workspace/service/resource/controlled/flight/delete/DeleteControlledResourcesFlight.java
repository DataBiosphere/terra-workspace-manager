package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.flight.create.GetAwsCloudContextStep;
import bio.terra.workspace.service.resource.controlled.flight.create.GetAzureCloudContextStep;
import bio.terra.workspace.service.resource.controlled.flight.create.GetGcpCloudContextStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;

/**
 * Flight for type-agnostic deletion of a controlled resource. All type-specific information should
 * live in individual steps.
 */
public class DeleteControlledResourcesFlight extends Flight {

  @Override
  public void addStep(Step step, RetryRule retryRule) {
    super.addStep(step, retryRule);
  }

  public DeleteControlledResourcesFlight(FlightMap inputParameters, Object beanBag)
      throws InterruptedException {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    UUID workspaceUuid =
        UUID.fromString(
            FlightUtils.getRequired(
                inputParameters, WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    var resourceStateRule =
        inputParameters.get(
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_STATE_RULE, WsmResourceStateRule.class);
    List<ControlledResource> controlledResources =
        inputParameters.get(
            ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE, new TypeReference<>() {});

    for (ControlledResource controlledResource : controlledResources) {
      addResourceDeleteSteps(flightBeanBag, controlledResource, workspaceUuid, resourceStateRule);
    }
  }

  /**
   * Generate the steps for deleting one of the resources on our incoming list.
   *
   * @param flightBeanBag
   * @param resource
   * @param workspaceUuid
   */
  protected void addResourceDeleteSteps(
      FlightBeanBag flightBeanBag,
      ControlledResource resource,
      UUID workspaceUuid,
      WsmResourceStateRule resourceStateRule) {
    final RetryRule cloudRetry = RetryRules.cloud();
    final RetryRule dbRetry = RetryRules.shortDatabase();

    addStep(
        new DeleteMetadataStartStep(
            flightBeanBag.getResourceDao(), workspaceUuid, resource.getResourceId()),
        dbRetry);

    // Get the cloud context for the resource we are deleting
    switch (resource.getResourceType().getCloudPlatform()) {
      case GCP ->
          addStep(
              new GetGcpCloudContextStep(workspaceUuid, flightBeanBag.getGcpCloudContextService()),
              cloudRetry);
      case AZURE ->
          addStep(
              new GetAzureCloudContextStep(
                  workspaceUuid, flightBeanBag.getAzureCloudContextService()),
              cloudRetry);
      case AWS ->
          addStep(
              new GetAwsCloudContextStep(workspaceUuid, flightBeanBag.getAwsCloudContextService()),
              cloudRetry);
    }

    // Delete the cloud resource. This has unique logic for each resource type. Depending on the
    // specifics of the resource type, this step may require the flight to run asynchronously.
    resource.getDeleteSteps(getInputParameters(), flightBeanBag).forEach(step -> addStep(step, cloudRetry));

    // Delete the Sam resource. That will make the object inaccessible.
    addStep(
        new DeleteSamResourceStep(
            flightBeanBag.getResourceDao(),
            flightBeanBag.getSamService(),
            workspaceUuid,
            resource.getResourceId()),
        cloudRetry);

    // Delete the metadata
    addStep(
        new DeleteMetadataStep(
            flightBeanBag.getResourceDao(), workspaceUuid, resource.getResourceId()),
        RetryRules.shortDatabase());
  }
}
