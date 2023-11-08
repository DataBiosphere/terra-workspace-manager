package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleNone;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.create.GetAzureCloudContextStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.StepRetryRulePair;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CloneControlledAzureResourceFlight extends Flight {
  private static final Logger logger =
      LoggerFactory.getLogger(CloneControlledAzureResourceFlight.class);

  protected CloneControlledAzureResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

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

    List<StepRetryRulePair> resourceCloningSteps = new ArrayList<>();

    if (cloningInstructions != CloningInstructions.COPY_NOTHING) {
      // Get the cloud context and store it in the working map
      resourceCloningSteps.add(
          new StepRetryRulePair(
              new GetAzureCloudContextStep(
                  destinationWorkspaceId, flightBeanBag.getAzureCloudContextService()),
              RetryRules.shortDatabase()));

      // 1. Check user has read access to source resource
      // 2. Gather controlled resource metadata for source object
      // 3. Check if the resource is already present
      // 4. Create resource in new workspace

      resourceCloningSteps.add(
          new StepRetryRulePair(
              new CheckControlledResourceAuthStep(
                  sourceResource,
                  flightBeanBag.getControlledResourceMetadataManager(),
                  userRequest),
              RetryRules.shortExponential()));

      if (mergePolicies) {
        resourceCloningSteps.add(
            new StepRetryRulePair(
                new MergePolicyAttributesStep(
                    sourceResource.getWorkspaceId(),
                    destinationWorkspaceId,
                    cloningInstructions,
                    flightBeanBag.getTpsApiDispatch()),
                RetryRuleNone.getRetryRuleNone()));
      }

      resourceCloningSteps.add(
          new StepRetryRulePair(
              new RetrieveControlledResourceMetadataStep(
                  flightBeanBag.getResourceDao(),
                  sourceResource.getWorkspaceId(),
                  sourceResource.getResourceId()),
              RetryRuleNone.getRetryRuleNone()));

      // check that the resource does not already exist in the workspace
      // so we can reliably retry the copy definition step later on
      resourceCloningSteps.add(
          new StepRetryRulePair(
              new VerifyResourceDoesNotExist(flightBeanBag.getResourceDao()),
              RetryRuleNone.getRetryRuleNone()));
    }

    switch (cloningInstructions) {
      case COPY_DEFINITION -> resourceCloningSteps.addAll(
          copyDefinition(flightBeanBag, inputParameters));
      case COPY_RESOURCE -> {
        resourceCloningSteps.addAll(copyDefinition(flightBeanBag, inputParameters));
        resourceCloningSteps.addAll(copyResource(flightBeanBag, inputParameters));
      }
      case COPY_REFERENCE -> resourceCloningSteps.addAll(
          copyReference(flightBeanBag, inputParameters));
      case LINK_REFERENCE -> resourceCloningSteps.addAll(
          linkReference(flightBeanBag, inputParameters));
      case COPY_NOTHING -> resourceCloningSteps.addAll(copyNothing(flightBeanBag, inputParameters));
    }

    resourceCloningSteps.addAll(setCloneResponse(flightBeanBag, inputParameters));

    resourceCloningSteps.forEach(
        (stepRetryRulePair -> addStep(stepRetryRulePair.step(), stepRetryRulePair.retryRule())));
  }

  protected List<StepRetryRulePair> copyNothing(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    var sourceResource =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledResource.class);
    return List.of(
        new StepRetryRulePair(
            new SetNoOpResourceCloneResponseStep(sourceResource), RetryRules.shortExponential()));
  }

  protected List<StepRetryRulePair> copyDefinition(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    throw cloneUnsupported(CloningInstructions.COPY_DEFINITION, inputParameters);
  }

  protected List<StepRetryRulePair> copyResource(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    throw cloneUnsupported(CloningInstructions.COPY_RESOURCE, inputParameters);
  }

  protected List<StepRetryRulePair> copyReference(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    throw cloneUnsupported(CloningInstructions.COPY_REFERENCE, inputParameters);
  }

  protected List<StepRetryRulePair> linkReference(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    throw cloneUnsupported(CloningInstructions.LINK_REFERENCE, inputParameters);
  }

  protected List<StepRetryRulePair> setCloneResponse(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    return List.of(
        new StepRetryRulePair(new SetCloneFlightResponseStep(), RetryRuleNone.getRetryRuleNone()));
  }

  private static IllegalArgumentException cloneUnsupported(
      CloningInstructions cloningInstructions, FlightMap inputParameters) {
    WsmResourceType sourceResourceType =
        FlightUtils.getRequired(
                inputParameters,
                WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
                ControlledResource.class)
            .getResourceType();
    return new IllegalArgumentException(
        "Clone instruction %s not supported for resource type %s"
            .formatted(cloningInstructions.toSql(), sourceResourceType.toSql()));
  }
}
