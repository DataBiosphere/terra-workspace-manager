package bio.terra.workspace.service.resource.controlled.flight.newclone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.WsmFlight;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloneSourceMetadata;

import java.util.UUID;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CLONE_SOURCE_METADATA;

public class CloneWorkspaceFlight extends WsmFlight {
    // Inputs:
    //   userRequest - credentials of the cloning user
    //   cloneSourceMetadata - all of the source workspace metadata
    //   destinationWorkspace - destination workspace already created
    //   location - destination location for cloned controlled resources

    // Extract the input parameters we need
    UUID workspaceUuid =
        UUID.fromString(getInputRequired(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));


    // Flight Plan
    //  1. Set the destination workspace policies from the source workspace
    //  2. Make the cloud contexts in the destination workspace
    //  3. Create the folders in the destination workspace
    //  4. Compute all destination resources
    //  5. Clone the resources in sub-flights (could be in parallel)

    public CloneWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
        super(inputParameters, applicationContext);

        var userRequest = getInputRequired(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
        var cloneSourceMetadata = getInputRequired(CLONE_SOURCE_METADATA, CloneSourceMetadata.class);
        var destinationWorkspace = getInputRequired(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class);
        var location = getInputRequiredNullable(WorkspaceFlightMapKeys.ControlledResourceKeys.LOCATION, String.class);

        if (cloneSourceMetadata.getGcpCloudContext() != null) {
            beanBag().getGcpCloudContextService().makeCreateGcpContextSteps(this, workspaceUuid, userRequest);
        }

        // TODO: [PF-2107] Do the same fixup for Azure cloud context


    }


}
