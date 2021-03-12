package bio.terra.workspace.service.resource.referenced.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;

public class CreateReferenceVerifyAccessDataRepoSnapshotStep implements Step {
  private final DataRepoService dataRepoService;

  public CreateReferenceVerifyAccessDataRepoSnapshotStep(DataRepoService dataRepoService) {
    this.dataRepoService = dataRepoService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();

    AuthenticatedUserRequest userReq =
        inputMap.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    ReferencedDataRepoSnapshotResource referenceResource =
        inputMap.get(JobMapKeys.REQUEST.getKeyName(), ReferencedDataRepoSnapshotResource.class);

    String instanceName = referenceResource.getInstanceName();
    String snapshotId = referenceResource.getSnapshotId();

    if (!dataRepoService.snapshotExists(instanceName, snapshotId, userReq)) {
      throw new InvalidReferenceException(
          String.format(
              "Snapshot %s could not be found in Data Repo instance %s. Verify that your reference was correctly defined and the instance is correct",
              snapshotId, instanceName));
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
