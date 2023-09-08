package bio.terra.workspace.service.workspace.flight.cloud.aws;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;

public class MakeAwsCloudContextStep implements Step {
  private final AwsCloudContextService awsCloudContextService;
  private final SamService samService;
  private final SpendProfileId spendProfileId;

  public MakeAwsCloudContextStep(
      AwsCloudContextService awsCloudContextService,
      SamService samService,
      SpendProfileId spendProfileId) {
    this.awsCloudContextService = awsCloudContextService;
    this.samService = samService;
    this.spendProfileId = spendProfileId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    // AWS cloud context derives from the landing zone, so all we do it ask for the
    // information and store the created cloud context in the map. The shared finish
    // step will perform the database update.
    AwsCloudContext awsCloudContext =
        awsCloudContextService.createCloudContext(
            flightContext.getFlightId(),
            spendProfileId,
            FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService));
    flightContext.getWorkingMap().put(WorkspaceFlightMapKeys.CLOUD_CONTEXT, awsCloudContext);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Read-only step - no undo
    return StepResult.getStepResultSuccess();
  }
}
