package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.Step;

/**
 * Interface for steps involved in deleting a ControlledResource (returned by ControlledResource.getDeleteSteps()).
 * ControlledResource.getDeleteSteps() is typed as this instead of the broader Step interface to force acknowledgement
 *   when implementing deletion of new resource types.
 * The primary reason to extend this interface is to allow for common error handling or other patterns between
 * similar resources (such as in DeleteAzureControlledResourceStep).
 */
public interface DeleteControlledResourceStep extends Step {
}
