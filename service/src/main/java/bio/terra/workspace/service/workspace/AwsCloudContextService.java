package bio.terra.workspace.service.workspace;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.app.configuration.external.AwsConfiguration.Authentication;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.exceptions.InvalidApplicationConfigException;
import bio.terra.workspace.service.workspace.flight.cloud.aws.MakeAwsCloudContextStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.DeleteCloudContextResourceFlight;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;

/**
 * This service provides methods for managing AWS cloud context. These methods do not perform any
 * access control and operate directly against the {@link WorkspaceDao}
 */
@SuppressFBWarnings(
    value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
    justification = "Enable both injection and static lookup")
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
      FlightBeanBag appContext,
      UUID workspaceUuid,
      SpendProfile spendProfile,
      AuthenticatedUserRequest userRequest) {
    flight.addStep(
        new MakeAwsCloudContextStep(appContext.getAwsCloudContextService(), spendProfile.id()));
  }

  @Override
  public void addDeleteCloudContextSteps(
      DeleteCloudContextFlight flight,
      FlightBeanBag appContext,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest) {
    // No post-resource delete steps for AWS
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
  public void launchDeleteFlight( // TODO-Dex
      ControlledResourceService controlledResourceService,
      UUID workspaceUuid,
      UUID resourceId,
      String flightId,
      AuthenticatedUserRequest userRequest) {
    controlledResourceService
        .flexibleDeletionJobBuilder(
            flightId,
            workspaceUuid,
            resourceId,
            /* forceDelete= */ true,
            /* resultPath= */ null,
            userRequest,
            DeleteCloudContextResourceFlight.class)
        .submit();
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
  @Traced
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
    AwsCloudContext cloudContext =
        getAwsCloudContext(workspaceUuid)
            .orElseThrow(
                () -> new CloudContextRequiredException("Operation requires AWS cloud context"));
    return cloudContext;
  }

  /**
   * Return a new AWS cloud context for discovered environment. The context is set to the CREATING
   * state.
   *
   * @return AWS cloud context {@link AwsCloudContext}
   */
  public AwsCloudContext createCloudContext(String flightId, SpendProfileId spendProfileId) {
    return createCloudContext(flightId, spendProfileId, discoverEnvironment());
  }

  /**
   * Return a new AWS cloud context for discovered environment
   *
   * @param environment {@link Environment}
   * @return {@link AwsCloudContext}
   */
  private static AwsCloudContext createCloudContext(
      String flightId, SpendProfileId spendProfileId, Environment environment) {
    Metadata metadata = environment.getMetadata();
    return new AwsCloudContext(
        new AwsCloudContextFields(
            metadata.getMajorVersion(),
            metadata.getOrganizationId(),
            metadata.getAccountId(),
            metadata.getTenantAlias(),
            metadata.getEnvironmentAlias()),
        new CloudContextCommonFields(
            spendProfileId, WsmResourceState.CREATING, flightId, /*error=*/ null));
  }

  /**
   * Discover environment & return a verified environment
   *
   * @return AWS environment
   */
  public Environment discoverEnvironment() throws IllegalArgumentException, InternalLogicException {
    try {
      featureService.awsEnabledCheck();
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
   * @param awsCloudContext {@link AwsCloudContext}
   * @param region {@link Region}
   * @return AWS landing zone, if supported for the Cloud context region
   * @throws StaleConfigurationException StaleConfigurationException
   */
  public Optional<LandingZone> getLandingZone(AwsCloudContext awsCloudContext, Region region) {
    return getLandingZone(discoverEnvironment(), awsCloudContext, region);
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

  private synchronized void initializeEnvironmentDiscovery() {
    if (environmentDiscovery == null) {
      environmentDiscovery = AwsUtils.createEnvironmentDiscovery(awsConfiguration);
    }
  }
}
