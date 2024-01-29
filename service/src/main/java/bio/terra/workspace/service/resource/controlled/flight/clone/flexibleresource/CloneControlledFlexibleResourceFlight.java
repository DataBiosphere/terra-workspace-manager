package bio.terra.workspace.service.resource.controlled.flight.clone.flexibleresource;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.exception.CloneInstructionNotSupportedException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesDryRunStep;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesStep;
import bio.terra.workspace.service.policy.flight.ValidateWorkspaceAgainstPolicyStep;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.UUID;

// Flight Plan:

// Note: The flex resource attributes are immutable during cloning. Only the name and description
// can be modified.
// Precondition: In the controller, the user's access is validated, and the no-op response is
// handled. However, these steps are in this flight for consistency.

// 1. Check the user's access to the source resource.
// 2. If cloning instructions resolve to COPY_NOTHING, then exit and return an empty result.
// 3. If cloning instructions resolve to COPY_REFERENCE or COPY_DEFINITION, then throw a "not
// supported" error.
// 4. Merge the policies (if applicable).
// 5. Create the cloned flex resource with new name and description (call a CreateControlledResource
// flight).
public class CloneControlledFlexibleResourceFlight extends Flight {

  public CloneControlledFlexibleResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    final ControlledFlexibleResource sourceFlexResource =
        Preconditions.checkNotNull(
            inputParameters.get(
                WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, ControlledFlexibleResource.class));

    final CloningInstructions resolvedCloningInstructions =
        Optional.ofNullable(
                inputParameters.get(
                    WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
                    CloningInstructions.class))
            .orElse(sourceFlexResource.getCloningInstructions());

    boolean mergePolicies =
        Optional.ofNullable(
                inputParameters.get(WorkspaceFlightMapKeys.MERGE_POLICIES, Boolean.class))
            .orElse(false);

    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // 1. The user access check is performed in the controller, but double-check for consistency.
    addStep(
        new CheckControlledResourceAuthStep(
            sourceFlexResource, flightBeanBag.getControlledResourceMetadataManager(), userRequest),
        RetryRules.shortExponential());

    // 2. The empty API result is returned within the controller, but double-check for consistency.
    if (resolvedCloningInstructions == CloningInstructions.COPY_NOTHING) {
      return;
    }

    // 3. Non-supported cloning instructions.
    if (resolvedCloningInstructions == CloningInstructions.COPY_REFERENCE
        || resolvedCloningInstructions == CloningInstructions.COPY_DEFINITION) {
      throw new CloneInstructionNotSupportedException(
          String.format("Cloning Instructions %s not supported", resolvedCloningInstructions));
    }

    // 4. Merge policies
    if (mergePolicies) {
      final UUID destinationWorkspaceId =
          inputParameters.get(
              WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
      addStep(
          new MergePolicyAttributesDryRunStep(
              sourceFlexResource.getWorkspaceId(),
              destinationWorkspaceId,
              resolvedCloningInstructions,
              flightBeanBag.getTpsApiDispatch()));

      // Flexible resources do not have a location.
      // Regardless, this step will always be successful since they are ANY cloudPlatform
      addStep(
          new ValidateWorkspaceAgainstPolicyStep(
              destinationWorkspaceId,
              sourceFlexResource.getResourceType().getCloudPlatform(),
              /* destinationLocation= */ null,
              resolvedCloningInstructions,
              userRequest,
              flightBeanBag.getResourceDao(),
              flightBeanBag.getTpsApiDispatch()));

      addStep(
          new MergePolicyAttributesStep(
              sourceFlexResource.getWorkspaceId(),
              destinationWorkspaceId,
              resolvedCloningInstructions,
              flightBeanBag.getTpsApiDispatch()));
    }
    // 5. Create a new flexible resource in the destination with new name and description.
    addStep(
        new CloneFlexibleResourceStep(
            flightBeanBag.getSamService(),
            userRequest,
            flightBeanBag.getControlledResourceService(),
            sourceFlexResource,
            resolvedCloningInstructions));
  }
}
