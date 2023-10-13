package bio.terra.workspace.service.workspace.flight.cloud.aws;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.cloudres.aws.ec2.EC2SecurityGroupCow;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupRequest;
import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.ResourceType;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagSpecification;

public class CreateWorkspaceApplicationSecurityGroupsStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(CreateWorkspaceApplicationSecurityGroupsStep.class);
  private final CrlService crlService;
  private final SamService samService;
  private final AwsCloudContextService awsCloudContextService;
  private final UUID workspaceUuid;

  public CreateWorkspaceApplicationSecurityGroupsStep(
      CrlService crlService,
      AwsCloudContextService awsCloudContextService,
      SamService samService,
      UUID workspaceUuid) {
    this.crlService = crlService;
    this.samService = samService;
    this.awsCloudContextService = awsCloudContextService;
    this.workspaceUuid = workspaceUuid;
  }

  private TagSpecification generateTagSpecification(LandingZone landingZone, UUID workspaceUuid) {
    Metadata metadata = landingZone.getMetadata();
    return TagSpecification.builder()
        .resourceType(ResourceType.SECURITY_GROUP)
        .tags(
            List.of(
                Tag.builder().key(AwsUtils.TAG_KEY_TENANT).value(metadata.getTenantAlias()).build(),
                Tag.builder()
                    .key(AwsUtils.TAG_KEY_ENVIRONMENT)
                    .value(metadata.getEnvironmentAlias())
                    .build(),
                Tag.builder()
                    .key(AwsUtils.TAG_KEY_WORKSPACE_ID)
                    .value(workspaceUuid.toString())
                    .build()))
        .build();
  }

  /**
   * Attempts to find an existing security group in the AWS region that is tagged as belonging to
   * this workspace.
   *
   * @return An {@link Optional<String>} populated with the security group ID of the
   * @throws {@link InternalServerErrorException} if more than one security is found and tagged for
   *     this workspace.
   */
  private Optional<String> findSecurityGroupInRegion(
      EC2SecurityGroupCow regionCow, UUID workspaceUuid) {
    DescribeSecurityGroupsResponse response =
        regionCow.getByTag(AwsUtils.TAG_KEY_WORKSPACE_ID, workspaceUuid.toString());

    if (!response.hasSecurityGroups() || response.securityGroups().isEmpty())
      return Optional.empty();

    if (response.securityGroups().size() != 1) {
      throw new InternalServerErrorException(
          String.format(
              "Expected 0 or 1 security group for workspace, found %d.",
              response.securityGroups().size()));
    }

    String groupId = response.securityGroups().get(0).groupId();
    logger.info(
        "Found previously created EC2 Security Group ID '{}' for Workspace {}",
        groupId,
        workspaceUuid.toString());
    return Optional.of(response.securityGroups().get(0).groupId());
  }

  /**
   * Creates a new security group in the AWS region and tags it as belonging to this workspace.
   *
   * @return The ID of the newly created security group.
   */
  private String createSecurityGroupInRegion(
      EC2SecurityGroupCow regionCow, Region region, UUID workspaceUuid, LandingZone landingZone) {

    CreateSecurityGroupResponse response =
        regionCow.create(
            CreateSecurityGroupRequest.builder()
                .vpcId(landingZone.getApplicationVpcId().orElseThrow())
                .description(
                    String.format(
                        "Application Security Group for Workspace %s", workspaceUuid.toString()))
                .groupName(
                    String.format("ws-app-%s-%s", workspaceUuid.toString(), region.toString()))
                .tagSpecifications(generateTagSpecification(landingZone, workspaceUuid))
                .build());

    String groupId = response.groupId();
    logger.info(
        "Created EC2 Security Group ID '{}' for Workspace {}", groupId, workspaceUuid.toString());
    return groupId;
  }

  /**
   * Checks whether a security group exists in the AWS region for this workspace. If found, its ID
   * is returned. If not found, a new security group is created and tagged as belonging to this
   * workspace.
   *
   * @return The ID of the security group that was found or created.
   */
  private String findOrCreateSecurityGroupInRegion(
      AwsCredentialsProvider credentialsProvider,
      UUID workspaceUuid,
      Region region,
      LandingZone landingZone) {

    try (EC2SecurityGroupCow regionCow =
        EC2SecurityGroupCow.instanceOf(crlService.getClientConfig(), credentialsProvider, region)) {
      return findSecurityGroupInRegion(regionCow, workspaceUuid)
          .orElseGet(
              () -> createSecurityGroupInRegion(regionCow, region, workspaceUuid, landingZone));
    }
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    Environment awsEnvironment =
        awsCloudContextService.discoverEnvironment(
            FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService));

    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(), awsEnvironment);

    Map<String, String> regionSecurityGroups = new HashMap<>();

    // Create a security group in each landing zone.
    for (Region region : awsEnvironment.getSupportedRegions()) {
      Optional<LandingZone> landingZone = awsEnvironment.getLandingZone(region);

      // ID's of security groups are not known until after the security group is created.  Thus, in
      // order to make this step idempotent and not leak security groups, we need to check whether a
      // security group has already been created for this workspace in a prior invocation by looking
      // for a security group tagged for this workspace before we actually create a new one.

      String groupId =
          findOrCreateSecurityGroupInRegion(
              credentialsProvider, workspaceUuid, region, landingZone.orElseThrow());

      regionSecurityGroups.put(region.toString(), groupId);

      logger.info(
          "Mapped Security Group ID '{}' to Workspace {} (Landing Zone {})",
          groupId,
          workspaceUuid.toString(),
          region.toString());
    }

    flightContext
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.AWS_APPLICATION_SECURITY_GROUP_ID, regionSecurityGroups);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {

    Environment awsEnvironment =
        awsCloudContextService.discoverEnvironment(
            FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService));

    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(), awsEnvironment);

    for (Region region : awsEnvironment.getSupportedRegions()) {
      try (EC2SecurityGroupCow regionCow =
          EC2SecurityGroupCow.instanceOf(
              crlService.getClientConfig(), credentialsProvider, region)) {
        findSecurityGroupInRegion(regionCow, workspaceUuid)
            .ifPresent(
                securityGroupId -> {
                  AwsUtils.deleteWorkspaceSecurityGroup(
                      crlService.getClientConfig(),
                      credentialsProvider,
                      workspaceUuid,
                      region,
                      securityGroupId);
                });
      }
    }

    return StepResult.getStepResultSuccess();
  }
}
