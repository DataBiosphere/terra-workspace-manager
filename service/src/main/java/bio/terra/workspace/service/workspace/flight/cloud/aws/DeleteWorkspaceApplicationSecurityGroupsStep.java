package bio.terra.workspace.service.workspace.flight.cloud.aws;

import bio.terra.cloudres.aws.ec2.EC2SecurityGroupCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;

public class DeleteWorkspaceApplicationSecurityGroupsStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(DeleteWorkspaceApplicationSecurityGroupsStep.class);
  private final CrlService crlService;
  private final SamService samService;
  private final AwsCloudContextService awsCloudContextService;
  private final UUID workspaceUuid;

  public DeleteWorkspaceApplicationSecurityGroupsStep(
      CrlService crlService,
      AwsCloudContextService awsCloudContextService,
      SamService samService,
      UUID workspaceUuid) {
    this.crlService = crlService;
    this.samService = samService;
    this.awsCloudContextService = awsCloudContextService;
    this.workspaceUuid = workspaceUuid;
  }

  private void deleteSecurityGroup(
      FlightContext flightContext, Region region, String securityGroupId) {
    try (EC2SecurityGroupCow regionCow =
        EC2SecurityGroupCow.instanceOf(
            crlService.getClientConfig(),
            awsCloudContextService.getFlightCredentialsProvider(flightContext, samService),
            region)) {
      regionCow.delete(securityGroupId);

      logger.info(
          "Deleted security group {} in region {} for workspace {}",
          securityGroupId,
          region.toString(),
          workspaceUuid.toString());
    } catch (Ec2Exception e) {
      if (e.awsErrorDetails().errorCode().equals("InvalidGroup.NotFound")) {
        logger.info(
            "Security group {} in region {} for workspace {} already deleted",
            securityGroupId,
            region.toString(),
            workspaceUuid.toString());
      } else {
        throw e;
      }
    }
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    Optional.ofNullable(
            awsCloudContextService
                .getRequiredAwsCloudContext(workspaceUuid)
                .getContextFields()
                .getApplicationSecurityGroups())
        .ifPresent(
            applicationSecurityGroups -> {
              for (Map.Entry<String, String> entry : applicationSecurityGroups.entrySet()) {
                deleteSecurityGroup(flightContext, Region.of(entry.getKey()), entry.getValue());
              }
            });

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
