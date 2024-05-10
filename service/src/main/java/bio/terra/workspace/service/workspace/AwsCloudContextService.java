package bio.terra.workspace.service.workspace;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.stairway.FlightContext;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.app.configuration.external.AwsConfiguration.Authentication;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.exceptions.InvalidApplicationConfigException;
import bio.terra.workspace.service.workspace.flight.cloud.aws.CreateWorkspaceApplicationSecurityGroupsStep;
import bio.terra.workspace.service.workspace.flight.cloud.aws.DeleteWorkspaceApplicationSecurityGroupsStep;
import bio.terra.workspace.service.workspace.flight.cloud.aws.MakeAwsCloudContextStep;
import bio.terra.workspace.service.workspace.flight.cloud.aws.SetWorkspaceApplicationEgressIngressStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * This service provides methods for managing AWS cloud context. These methods do not perform any
 * access control and operate directly against the {@link WorkspaceDao}
 */
@Component
public class AwsCloudContextService implements CloudContextService {
  private final AwsConfiguration awsConfiguration;
  private final WorkspaceDao workspaceDao;
  private final FeatureService featureService;
  private final ResourceDao resourceDao;
  private EnvironmentDiscovery environmentDiscovery;
  private static AwsCloudContextService theService;

  @Autowired
  public AwsCloudContextService(
      WorkspaceDao workspaceDao,
      FeatureService featureService,
      AwsConfiguration awsConfiguration,
      ResourceDao resourceDao) {
    this.awsConfiguration = awsConfiguration;
    this.workspaceDao = workspaceDao;
    this.featureService = featureService;
    this.resourceDao = resourceDao;
  }

  // Set up static accessor for use by CloudPlatform
  @PostConstruct
  public void postConstruct() {
    theService = this;
  }

  public static AwsCloudContextService getTheService() {
    return theService;
  }

  @Override
  public void addCreateCloudContextSteps(
      CreateCloudContextFlight flight,
      FlightBeanBag flightBeanBag,
      UUID workspaceUuid,
      SpendProfile spendProfile,
      AuthenticatedUserRequest userRequest) {
    if (featureService.isFeatureEnabled(FeatureService.AWS_APPLICATIONS_ENABLED)) {
      flight.addStep(
          new CreateWorkspaceApplicationSecurityGroupsStep(
              flightBeanBag.getCrlService(),
              flightBeanBag.getAwsCloudContextService(),
              flightBeanBag.getSamService(),
              workspaceUuid),
          RetryRules.cloud());
      flight.addStep(
          new SetWorkspaceApplicationEgressIngressStep(
              flightBeanBag.getCrlService(),
              flightBeanBag.getAwsCloudContextService(),
              flightBeanBag.getSamService()),
          RetryRules.cloud());
    } // End if AWS_APPLICATIONS_ENABLED
    flight.addStep(
        new MakeAwsCloudContextStep(
            flightBeanBag.getAwsCloudContextService(),
            flightBeanBag.getSamService(),
            spendProfile.id()),
        RetryRules.shortDatabase());
  }

  @Override
  public void addDeleteCloudContextSteps(
      DeleteCloudContextFlight flight,
      FlightBeanBag flightBeanBag,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest) {

    // Always add the step to delete Workspace Security Groups; it has internal logic to handle the
    // case where there are no Security Groups present.
    flight.addStep(
        new DeleteWorkspaceApplicationSecurityGroupsStep(
            flightBeanBag.getCrlService(),
            flightBeanBag.getAwsCloudContextService(),
            flightBeanBag.getSamService(),
            workspaceUuid),
        RetryRules.cloud());
  }

  @Override
  public CloudContext makeCloudContextFromDb(DbCloudContext dbCloudContext) {
    return AwsCloudContext.deserialize(dbCloudContext);
  }

  @Override
  public List<ControlledResource> makeOrderedResourceList(UUID workspaceUuid) {
    return resourceDao.listControlledResources(workspaceUuid, CloudPlatform.AWS);
  }

  @Override
  public void launchDeleteResourceFlight(
      ControlledResourceService controlledResourceService,
      UUID workspaceUuid,
      UUID resourceId,
      String flightId,
      AuthenticatedUserRequest userRequest) {
    controlledResourceService.deleteControlledResourceAsync(
        flightId,
        workspaceUuid,
        resourceId,
        /* forceDelete= */ true,
        /* resultPath= */ null,
        userRequest);
  }

  /** Returns authentication from configuration */
  public Authentication getRequiredAuthentication() {
    if (awsConfiguration == null) {
      throw new InvalidApplicationConfigException("AWS configuration not initialized");
    }
    return awsConfiguration.getAuthentication();
  }

  /**
   * Retrieve the optional AWS cloud context for given workspace.
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional {@link AwsCloudContext}
   */
  @WithSpan
  public Optional<AwsCloudContext> getAwsCloudContext(UUID workspaceUuid) {
    return workspaceDao
        .getCloudContext(workspaceUuid, CloudPlatform.AWS)
        .map(AwsCloudContext::deserialize);
  }

  /**
   * Retrieve the required AWS cloud context
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return {@link AwsCloudContext}
   * @throws CloudContextRequiredException CloudContextRequiredException
   */
  public AwsCloudContext getRequiredAwsCloudContext(UUID workspaceUuid) {
    return getAwsCloudContext(workspaceUuid)
        .orElseThrow(
            () -> new CloudContextRequiredException("Operation requires AWS cloud context"));
  }

  /**
   * Return a new AWS cloud context for discovered environment. The context is set to the CREATING
   * state.
   *
   * @return AWS cloud context {@link AwsCloudContext}
   */
  public AwsCloudContext createCloudContext(
      String flightId,
      SpendProfileId spendProfileId,
      String userEmail,
      @Nullable Map<String, String> securityGroupId) {
    return createCloudContext(
        flightId, spendProfileId, discoverEnvironment(userEmail), securityGroupId);
  }

  /**
   * Return a new AWS cloud context for discovered environment
   *
   * @param environment {@link Environment}
   * @return {@link AwsCloudContext}
   */
  public static AwsCloudContext createCloudContext(
      String flightId,
      SpendProfileId spendProfileId,
      Environment environment,
      @Nullable Map<String, String> securityGroupId) {
    Metadata metadata = environment.getMetadata();
    return new AwsCloudContext(
        new AwsCloudContextFields(
            metadata.getMajorVersion(),
            metadata.getOrganizationId(),
            metadata.getAccountId(),
            metadata.getTenantAlias(),
            metadata.getEnvironmentAlias(),
            securityGroupId),
        new CloudContextCommonFields(
            spendProfileId, WsmResourceState.CREATING, flightId, /* error= */ null));
  }

  /**
   * Discover environment & return a verified environment
   *
   * @param userEmail user email address
   * @return AWS environment
   */
  public Environment discoverEnvironment(String userEmail)
      throws IllegalArgumentException, InternalLogicException {
    try {
      featureService.featureEnabledCheck(FeatureService.AWS_ENABLED, userEmail);
      initializeEnvironmentDiscovery();

      if (this.environmentDiscovery == null) {
        throw new InvalidApplicationConfigException("AWS environmentDiscovery not initialized");
      }
      return environmentDiscovery.discoverEnvironment();
    } catch (IOException e) {
      throw new InternalLogicException("AWS discover environment error", e);
    }
  }

  /**
   * Return the landing zone to for the given cloud context's region
   *
   * @param environment {@link Environment}
   * @param awsCloudContext {@link AwsCloudContext}
   * @param region {@link Region}
   * @return {@link LandingZone}, if supported for the Cloud context region
   * @throws StaleConfigurationException StaleConfigurationException
   */
  public static Optional<LandingZone> getLandingZone(
      Environment environment, AwsCloudContext awsCloudContext, Region region) {
    awsCloudContext.verifyCloudContext(environment);
    return environment.getLandingZone(region);
  }

  /**
   * Get an {@link AwsCredentialsProvider} instance for a given flight
   *
   * @param flightContext context for a given flight
   * @param samService Sam service
   * @return {@link AwsCredentialsProvider} for the user that the flight is running on behalf of
   */
  public AwsCredentialsProvider getFlightCredentialsProvider(
      FlightContext flightContext, SamService samService) {
    return AwsUtils.createWsmCredentialProvider(
        getRequiredAuthentication(),
        discoverEnvironment(
            FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService)));
  }

  private synchronized void initializeEnvironmentDiscovery() {
    if (environmentDiscovery == null) {
      environmentDiscovery = AwsUtils.createEnvironmentDiscovery(awsConfiguration);
    }
  }
}
