package bio.terra.workspace.service.resource.referenced.flight.clone;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesDryRunStep;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesStep;
import bio.terra.workspace.service.policy.flight.ValidateWorkspaceAgainstPolicyStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneReferencedResourceStep;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Optional;
import java.util.UUID;

/** A flight to clone a reference resource including policy checks, if available. */
public class CloneReferenceResourceFlight extends Flight {
  /**
   * Flight to clone a reference resource.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public CloneReferenceResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(beanBag);
    RetryRule shortDatabaseRetryRule = RetryRules.shortDatabase();

    final UUID destinationWorkspaceId =
        FlightUtils.getRequired(
            inputParameters, ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    final UUID destinationResourceId =
        FlightUtils.getRequired(
            inputParameters, ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);

    final UUID destinationFolderId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_FOLDER_ID, UUID.class);

    final ReferencedResource sourceResource =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ReferencedResource.class);

    final AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);

    final boolean mergePolicies =
        Optional.ofNullable(
                inputParameters.get(WorkspaceFlightMapKeys.MERGE_POLICIES, Boolean.class))
            .orElse(false);

    if (mergePolicies) {
      addStep(
          new MergePolicyAttributesDryRunStep(
              destinationWorkspaceId,
              sourceResource.getWorkspaceId(),
              userRequest,
              appContext.getTpsApiDispatch()));

      addStep(
          new ValidateWorkspaceAgainstPolicyStep(
              destinationWorkspaceId,
              sourceResource.getResourceType().getCloudPlatform(),
              userRequest,
              appContext.getResourceDao(),
              appContext.getTpsApiDispatch()));

      addStep(
          new MergePolicyAttributesStep(
              destinationWorkspaceId,
              sourceResource.getWorkspaceId(),
              userRequest,
              appContext.getTpsApiDispatch()));
    }

    addStep(
        new CloneReferencedResourceStep(
            userRequest,
            appContext.getSamService(),
            appContext.getReferencedResourceService(),
            sourceResource,
            destinationResourceId,
            destinationFolderId),
        shortDatabaseRetryRule);

    if (mergePolicies) {
      // validate again after the clone in case other resources were added elsewhere.
      addStep(
          new ValidateWorkspaceAgainstPolicyStep(
              destinationWorkspaceId,
              sourceResource.getResourceType().getCloudPlatform(),
              userRequest,
              appContext.getResourceDao(),
              appContext.getTpsApiDispatch()));
    }

    addStep(new SetCloneReferenceResourceResponseStep());
  }
}
