package bio.terra.workspace.service.resource.controlled.flight.newclone.workspace;

import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.OperationType;

import java.util.Optional;

public class RunCreateControlledResourceFlightStep implements Step {
  private final JobService jobService;
  private final StairwayComponent stairwayComponent;
  private final AuthenticatedUserRequest userRequest;

  public RunCreateControlledResourceFlightStep(
    AuthenticatedUserRequest userRequest,
    JobService jobService,
    StairwayComponent stairwayComponent) {
    this.userRequest = userRequest;
    this.jobService = jobService;
    this.stairwayComponent = stairwayComponent;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    ControlledResource resource =
      workingMap.get(WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE, ControlledResource.class);

    // For clone, we use the resource id as the job id
    String jobId = resource.getResourceId().toString();

    // If the flight does not exist, launch it.
    if (!doesFlightExist(jobId)) {
      // We do not know the proper types of the parameter variable, so we get and put the
      // raw serialized string to set it up in the right place for the create flight.
      String rawParameters = workingMap.getRaw(WorkspaceFlightMapKeys.ControlledResourceKeys.RESOURCE_PARAMETERS);

      final String jobDescription =
        String.format(
          "Create cloned controlled resource %s; id %s; name %s",
          resource.getResourceType(), resource.getResourceId(), resource.getName());

      jobService.newJob()
        .description(jobDescription)
        .jobId(resource.getResourceId().toString())
        .flightClass(CreateControlledResourceFlight.class)
        .resource(resource)
        .userRequest(userRequest)
        .operationType(OperationType.CREATE)
        .workspaceId(resource.getWorkspaceId().toString())
        .resourceName(resource.getName())
        .resourceType(resource.getResourceType())
        .stewardshipType(resource.getStewardshipType())
        .addParameterRaw(WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS, rawParameters)
        .submit();
    }

    // TODO: PF-2107 the original clone code retries on an expired wait. Seems that a better approach
    //  would be to set a longer wait time. Right now all flights have the same configured max
    //  timeout. We could add timeout settings associated with each flight to allow each to set
    //  the appropriate timeout.
    jobService.waitForJob(jobId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }

  private boolean doesFlightExist(String flightId) throws InterruptedException {
    try {
      stairwayComponent.get().getFlightState(flightId);
      return true;
    } catch (FlightNotFoundException ex) {
      return false;
    }
  }
}
