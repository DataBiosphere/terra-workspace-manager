package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

/**
 * Flight for creation of a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class CreateControlledResourceFlight extends Flight {

  private final RetryRule dbRetryRule = RetryRules.shortDatabase();

  // addStep is protected in Flight, so make an override that is public
  @Override
  public void addStep(Step step, RetryRule retryRule) {
    super.addStep(step, retryRule);
  }

  public CreateControlledResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    FlightUtils.validateRequiredEntries(
        inputParameters,
        ResourceKeys.RESOURCE,
        ResourceKeys.RESOURCE_STATE_RULE,
        JobMapKeys.AUTH_USER_INFO.getKeyName());

    ControlledResource resource =
        FlightUtils.getRequired(inputParameters, ResourceKeys.RESOURCE, ControlledResource.class);
    var userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    // Role is optionally populated for private resources
    var privateResourceIamRole =
        inputParameters.get(
            ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLE, ControlledResourceIamRole.class);
    // PetSA is optional for some resources
    var petSaEmail =
        inputParameters.get(ControlledResourceKeys.CLOUD_PET_SERVICE_ACCOUNT, String.class);
    var resourceStateRule =
        FlightUtils.getRequired(
            inputParameters, ResourceKeys.RESOURCE_STATE_RULE, WsmResourceStateRule.class);

    // Store the resource metadata in the WSM database. Doing this first means concurrent
    // conflicting resources with the same name or resource attributes can be prevented.
    addStep(
        new CreateResourceInDbStartStep(
            flightBeanBag.getResourceDao(), resourceStateRule, resource),
        dbRetryRule);

    // create the Sam resource associated with the resource
    addStep(
        new CreateSamResourceStep(
            flightBeanBag.getSamService(),
            resource,
            privateResourceIamRole,
            resource.getAssignedUser().orElse(null),
            userRequest));

    // Get the cloud context and store it in the working map
    UUID workspaceUuid = resource.getWorkspaceId();
    switch (resource.getResourceType().getCloudPlatform()) {
      case GCP ->
          addStep(
              new GetGcpCloudContextStep(workspaceUuid, flightBeanBag.getGcpCloudContextService()),
              dbRetryRule);
      case AZURE ->
          addStep(
              new GetAzureCloudContextStep(
                  workspaceUuid, flightBeanBag.getAzureCloudContextService()),
              dbRetryRule);
      case AWS ->
          addStep(
              new GetAwsCloudContextStep(workspaceUuid, flightBeanBag.getAwsCloudContextService()),
              dbRetryRule);
    }

    // Tell the resource to make its specific steps
    resource.addCreateSteps(this, petSaEmail, userRequest, flightBeanBag);

    // Update private_resource_state from INITIALIZING to ACTIVE, if this is a private resource.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      addStep(
          new MarkPrivateResourceReadyStep(resource, flightBeanBag.getResourceDao()),
          RetryRules.shortDatabase());
    }

    // Complete the create and populate the return response
    addStep(
        new SetCreateResponseStep(
            resource,
            flightBeanBag.getResourceDao(),
            resourceStateRule,
            userRequest,
            flightBeanBag.getWorkspaceActivityLogService()));
  }
}
