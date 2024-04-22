package bio.terra.workspace.service.workspace.flight.cloud.aws;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

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

    FlightMap workingMap = flightContext.getWorkingMap();

    // If security groups were created, add them to the cloud context.
    Map<String, String> regionSecurityGroups =
        workingMap.get(
            WorkspaceFlightMapKeys.AWS_APPLICATION_SECURITY_GROUP_ID,
            new TypeReference<Map<String, String>>() {});

    // AWS cloud context derives from the landing zone, so all we do it ask for the
    // information and store the created cloud context in the map. The shared finish
    // step will perform the database update.
    AwsCloudContext awsCloudContext =
        awsCloudContextService.createCloudContext(
            flightContext.getFlightId(),
            spendProfileId,
            FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService),
            regionSecurityGroups);
    workingMap.put(WorkspaceFlightMapKeys.CLOUD_CONTEXT, awsCloudContext);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Read-only step - no undo
    return StepResult.getStepResultSuccess();
  }
}
