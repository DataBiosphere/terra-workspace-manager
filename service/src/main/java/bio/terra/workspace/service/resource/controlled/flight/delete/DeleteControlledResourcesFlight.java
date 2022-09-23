package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.flight.create.GetCloudContextStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;

/**
 * Flight for type-agnostic deletion of a controlled resource. All type-specific information should
 * live in individual steps.
 */
public class DeleteControlledResourcesFlight extends Flight {

  // addStep is protected in Flight, so make an override that is public
  @Override
  public void addStep(Step step, RetryRule retryRule) {
    super.addStep(step, retryRule);
  }

  public DeleteControlledResourcesFlight(FlightMap inputParameters, Object beanBag)
      throws InterruptedException {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    final UUID workspaceUuid =
        UUID.fromString(
            FlightUtils.getRequired(
                inputParameters, WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    final List<ControlledResource> resources =
        inputParameters.get(ControlledResourceKeys.RESOURCES_TO_DELETE, new TypeReference<>() {});
    final AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    final RetryRule cloudRetry = RetryRules.cloud();

    boolean hasAzureResource = false;
    boolean hasGcpResource = false;
    for (ControlledResource resource : resources) {
      CloudPlatform cloudPlatform = resource.getResourceType().getCloudPlatform();
      switch (cloudPlatform) {
        case GCP -> hasGcpResource = true;
        case AZURE -> hasAzureResource = true;
        case ANY -> throw new InternalLogicException(
            "Invalid cloud platform for controlled resource: " + cloudPlatform);
      }
    }
    if (hasAzureResource) {
      // Get the cloud context for the resource we are deleting
      addStep(
          new GetCloudContextStep(
              workspaceUuid,
              CloudPlatform.AZURE,
              flightBeanBag.getGcpCloudContextService(),
              flightBeanBag.getAzureCloudContextService(),
              userRequest),
          cloudRetry);
    }
    if (hasGcpResource) {
      addStep(
          new GetCloudContextStep(
              workspaceUuid,
              CloudPlatform.GCP,
              flightBeanBag.getGcpCloudContextService(),
              flightBeanBag.getAzureCloudContextService(),
              userRequest),
          cloudRetry);
    }
    for (ControlledResource resource : resources) {

      // Delete the cloud resource. This has unique logic for each resource type. Depending on the
      // specifics of the resource type, this step may require the flight to run asynchronously.
      resource.addDeleteSteps(this, flightBeanBag);

      // Delete the Sam resource. That will make the object inaccessible.
      addStep(
          new DeleteSamResourceStep(
              flightBeanBag.getResourceDao(),
              flightBeanBag.getSamService(),
              workspaceUuid,
              resource.getResourceId(),
              userRequest));

      // Delete the metadata
      addStep(
          new DeleteMetadataStep(
              flightBeanBag.getResourceDao(), workspaceUuid, resource.getResourceId()),
          RetryRules.shortDatabase());
    }
  }
}
