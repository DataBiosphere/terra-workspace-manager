package bio.terra.workspace.service.resource.controlled.flight.newclone.workspace;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.WsmFlight;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.flight.clone.ClonePolicyAttributesStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

public class CloneControlledResourceFlight extends WsmFlight {

  public CloneControlledResourceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    // Extract the input parameters
    var userRequest =
        getInputRequired(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var sourceResource =
        getInputRequired(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, ControlledResource.class);
    var destinationFields =
        getInputRequired(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_FIELDS,
            ControlledResourceFields.class);
    var destinationParameters =
        getInputRequired(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_PARAMETERS,
            ControlledGcsBucketParameters.class);
    boolean mergePolicies = getInputRequired(WorkspaceFlightMapKeys.MERGE_POLICIES, Boolean.class);
    var cloningInstructions =
        getInputRequired(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONING_INSTRUCTIONS,
            CloningInstructions.class);

    // Merge policies if TPS is enabled and the workspaces are different
    if (mergePolicies
        && (!sourceResource.getWorkspaceId().equals(destinationFields.getWorkspaceId()))) {
      addStep(
          new ClonePolicyAttributesStep(
              sourceResource.getWorkspaceId(),
              destinationFields.getWorkspaceId(),
              userRequest,
              beanBag().getTpsApiDispatch()));
    }

    // Ask the source resource to generate steps that will construct the destination resource
    // object.
    //     The result should be:
    //      - DESTINATION_RESOURCE in the working map (e.g., ControlledGcsBucket object)
    //      - RESOURCE_PARAMETERS in the working map (e.g., filled in ControlledGcsBucketParameters)
    sourceResource.addBuildCloneDestinationControlledResourceSteps(
        this, inputParameters, userRequest, destinationFields, destinationParameters);

    // Run the create controlled resource flight




    if (cloningInstructions == CloningInstructions.COPY_RESOURCE) {
      // Add data copying steps
    }
  }
}
