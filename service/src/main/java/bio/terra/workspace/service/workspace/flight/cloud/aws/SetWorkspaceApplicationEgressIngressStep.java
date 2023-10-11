package bio.terra.workspace.service.workspace.flight.cloud.aws;

import bio.terra.cloudres.aws.ec2.CrlEC2DuplicateSecurityRuleException;
import bio.terra.cloudres.aws.ec2.EC2SecurityGroupCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupEgressRequest;
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.UserIdGroupPair;

public class SetWorkspaceApplicationEgressIngressStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(SetWorkspaceApplicationEgressIngressStep.class);
  private final CrlService crlService;
  private final SamService samService;
  private final AwsCloudContextService awsCloudContextService;

  public SetWorkspaceApplicationEgressIngressStep(
      CrlService crlService, AwsCloudContextService awsCloudContextService, SamService samService) {
    this.crlService = crlService;
    this.samService = samService;
    this.awsCloudContextService = awsCloudContextService;
  }

  private void authorizeEgress(EC2SecurityGroupCow regionCow, String securityGroupId) {
    try {
      regionCow.authorizeEgress(
          AuthorizeSecurityGroupEgressRequest.builder()
              .groupId(securityGroupId)
              .ipPermissions(
                  // The combination of toPort(0), fromPort(0), and ipProtocol("-1") is used to
                  // indicate that outbound traffic to all ports and protocols are allowed to the IP
                  // ranges specified in ipRanges (in this case, egress is allowed to all
                  // destinations.
                  IpPermission.builder()
                      .fromPort(0)
                      .toPort(0)
                      .ipProtocol("-1")
                      .ipRanges(IpRange.builder().cidrIp("0.0.0.0/0").build())
                      .build())
              .build());

      logger.info("Allowed egress to all destinations for Security Group {}", securityGroupId);
    } catch (CrlEC2DuplicateSecurityRuleException e) {
      // This is expected, as we may race with creation of a default egress rule, which sets the
      // same IP permissions.  It may also happen when re-playing the step on failure; this is also
      // OK as the end result is the same.
      logger.info("Ignoring duplicate egress rule creation error");
    }
  }

  private void authorizeIngress(EC2SecurityGroupCow regionCow, String securityGroupId) {
    try {
      regionCow.authorizeIngress(
          AuthorizeSecurityGroupIngressRequest.builder()
              .groupId(securityGroupId)
              .ipPermissions(
                  // The combination of toPort(0), fromPort(0), and ipProtocol("-1") is used to
                  // indicate that inbound traffic from all ports and protocols are allowed from
                  // hosts in the security group specified in userIdGroupPairs (in this case,
                  // ingress is allowed only from other hosts in this same security group).
                  IpPermission.builder()
                      .fromPort(0)
                      .toPort(0)
                      .ipProtocol("-1")
                      .userIdGroupPairs(UserIdGroupPair.builder().groupId(securityGroupId).build())
                      .build())
              .build());
      logger.info(
          "Allowed ingress only from other hosts internally on Security Group {}", securityGroupId);
    } catch (CrlEC2DuplicateSecurityRuleException e) {
      // This is expected when re-playing the step on failure; this is OK as the end result is the
      // same.
      logger.info("Ignoring duplicate ingress rule creation error");
    }
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    Map<String, String> regionSecurityGroups =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(),
            WorkspaceFlightMapKeys.AWS_APPLICATION_SECURITY_GROUP_ID,
            new TypeReference<Map<String, String>>() {});

    for (Map.Entry<String, String> entry : regionSecurityGroups.entrySet()) {
      try (EC2SecurityGroupCow regionCow =
          EC2SecurityGroupCow.instanceOf(
              crlService.getClientConfig(),
              awsCloudContextService.getFlightCredentialsProvider(flightContext, samService),
              Region.of(entry.getKey()))) {
        String securityGroupId = entry.getValue();
        authorizeEgress(regionCow, securityGroupId);
        authorizeIngress(regionCow, securityGroupId);
      }
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // No need to do anything here as if we're rolling back the flight, the Security Group creation
    // undo step will delete the Security Group.
    return StepResult.getStepResultSuccess();
  }
}
