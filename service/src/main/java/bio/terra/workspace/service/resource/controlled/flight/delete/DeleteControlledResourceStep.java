package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.Step;

/**
 * Interface for steps involved in deleting a ControlledResource (returned by
 * ControlledResource.getDeleteSteps()). This interface was created out of a need to add specific
 * error handling to all Azure resource deletion, in cases where the underlying resources have
 * already been deleted. ControlledResource.getDeleteSteps() returns this interface to help
 * awareness of extensions to this type when implementing deletion of new resource types, and force
 * consideration of requirements that may be unique to deletion.
 */
public interface DeleteControlledResourceStep extends Step {}
