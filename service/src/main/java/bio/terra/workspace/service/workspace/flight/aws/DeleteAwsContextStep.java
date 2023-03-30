package bio.terra.workspace.service.workspace.flight.aws;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAwsContextStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteAwsContextStep.class);
  private final AwsCloudContextService awsCloudContextService;
  private final UUID workspaceUuid;

  public DeleteAwsContextStep(AwsCloudContextService awsCloudContextService, UUID workspaceUuid) {
    this.awsCloudContextService = awsCloudContextService;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (awsCloudContextService.getAwsCloudContext(workspaceUuid).isPresent()) {
      awsCloudContextService.deleteAwsCloudContext(workspaceUuid);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    logger.error("Unable to undo DAO deletion of aws context [{}]", workspaceUuid);
    return context.getResult();
  }
}
