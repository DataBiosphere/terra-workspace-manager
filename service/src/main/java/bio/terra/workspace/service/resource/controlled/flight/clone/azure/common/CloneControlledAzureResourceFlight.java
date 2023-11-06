package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity.CloneControlledAzureManagedIdentityResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.create.GetAzureCloudContextStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class CloneControlledAzureResourceFlight<T extends ControlledResource> extends Flight {
  private static final Logger logger =
          LoggerFactory.getLogger(CloneControlledAzureResourceFlight.class);

  protected CloneControlledAzureResourceFlight(
      FlightMap inputParameters, Object applicationContext, WsmResourceType wsmResourceType) {
    super(inputParameters, applicationContext);

    logger.info("(sanity check) CloneControlledAzureResourceFlight constructor has been called for resource type {}", wsmResourceType);

    logger.info("inputParameters {}", inputParameters.toString());
    logger.info("applicationContext {}", applicationContext.toString());

    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_NAME,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    logger.info("required entries validated");

    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

    logger.info("beanbag constructed");

    var sourceResource =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledResource.class);
    var userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    boolean mergePolicies =
        Optional.ofNullable(
                inputParameters.get(WorkspaceFlightMapKeys.MERGE_POLICIES, Boolean.class))
            .orElse(false);
    var cloningInstructions =
        Optional.ofNullable(
                inputParameters.get(
                    WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
                    CloningInstructions.class))
            .orElse(sourceResource.getCloningInstructions());

    logger.info("inputs extracted from map");
    logger.info("cloning instructions: {}", cloningInstructions);

    if (CloningInstructions.COPY_NOTHING == cloningInstructions) {
      copyNothing(flightBeanBag, inputParameters);
      return;
    }

    logger.info("azure cloud context: {}", flightBeanBag.getAzureCloudContextService());

    // Get the cloud context and store it in the working map
    addStep(
        new GetAzureCloudContextStep(
            destinationWorkspaceId, flightBeanBag.getAzureCloudContextService()),
        RetryRules.shortDatabase());

    // 1. Check user has read access to source resource
    // 2. Gather controlled resource metadata for source object
    // 3. Check if the resource is already present
    // 4. Create resource in new workspace

    addStep(
        new CheckControlledResourceAuthStep(
            sourceResource, flightBeanBag.getControlledResourceMetadataManager(), userRequest),
        RetryRules.shortExponential());

    if (mergePolicies) {
      addStep(
          new MergePolicyAttributesStep(
              sourceResource.getWorkspaceId(),
              destinationWorkspaceId,
              cloningInstructions,
              flightBeanBag.getTpsApiDispatch()));
    }

    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId()));

    // check that the resource does not already exist in the workspace
    // so we can reliably retry the copy definition step later on
    addStep(new VerifyResourceDoesNotExist(flightBeanBag.getResourceDao()));

    switch (cloningInstructions) {
      case COPY_DEFINITION -> copyDefinition(flightBeanBag, inputParameters);
      case COPY_RESOURCE -> {
        copyDefinition(flightBeanBag, inputParameters);
        copyResource(flightBeanBag, inputParameters);
      }
      case COPY_REFERENCE ->  copyReference(flightBeanBag, inputParameters);
      case LINK_REFERENCE -> linkReference(flightBeanBag, inputParameters);
      case COPY_NOTHING -> throw new IllegalStateException("Copy nothing instruction should not reach here.");
    }
    var stringSteps = getSteps().stream().map(Step::toString).collect(Collectors.joining("\n - "));
    logger.info("steps added:\n - {}", stringSteps);
  }

  protected void copyNothing(FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    var sourceResource = FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledResource.class);
    addStep(new SetNoOpResourceCloneResponseStep<>(sourceResource) {});
  }

  protected abstract void copyDefinition(FlightBeanBag flightBeanBag, FlightMap inputParameters);

  protected abstract void copyResource(FlightBeanBag flightBeanBag, FlightMap inputParameters);
  protected abstract void copyReference(FlightBeanBag flightBeanBag, FlightMap inputParameters);
  protected abstract void linkReference(FlightBeanBag flightBeanBag, FlightMap inputParameters);

}
