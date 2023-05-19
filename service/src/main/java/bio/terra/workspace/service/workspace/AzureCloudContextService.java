package bio.terra.workspace.service.workspace;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.flight.cloud.azure.DeleteControlledAzureResourcesStep;
import bio.terra.workspace.service.workspace.flight.cloud.azure.ValidateLandingZoneRegionAgainstPolicyStep;
import bio.terra.workspace.service.workspace.flight.cloud.azure.ValidateMRGStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service provides methods for managing Azure cloud context. These methods do not perform any
 * access control and operate directly against the {@link WorkspaceDao}
 */
@SuppressFBWarnings(
    value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
    justification = "Enable both injection and static lookup")
@Component
public class AzureCloudContextService implements CloudContextService {
  private static AzureCloudContextService theService;

  private final WorkspaceDao workspaceDao;
  private final FeatureConfiguration featureConfiguration;
  private final WorkspaceService workspaceService;

  @Autowired
  public AzureCloudContextService(
      WorkspaceDao workspaceDao,
      FeatureConfiguration featureConfiguration,
      WorkspaceService workspaceService) {
    this.workspaceDao = workspaceDao;
    this.featureConfiguration = featureConfiguration;
    this.workspaceService = workspaceService;
  }

  // Set up static accessor for use by CloudPlatform
  @PostConstruct
  public void postConstruct() {
    theService = this;
  }

  public static AzureCloudContextService getTheService() {
    return theService;
  }

  @Override
  public void addCreateCloudContextSteps(
      CreateCloudContextFlight flight,
      FlightBeanBag appContext,
      UUID workspaceUuid,
      SpendProfile spendProfile,
      AuthenticatedUserRequest userRequest) {
    if (featureConfiguration.isTpsEnabled()) {
      flight.addStep(
          new ValidateLandingZoneRegionAgainstPolicyStep(
              appContext.getLandingZoneApiDispatch(),
              userRequest,
              appContext.getTpsApiDispatch(),
              workspaceUuid,
              workspaceService));
    }

    // validate the MRG
    flight.addStep(
        new ValidateMRGStep(appContext.getCrlService(), appContext.getAzureConfig(), spendProfile));
  }

  @Override
  public void addDeleteCloudContextSteps(
      DeleteCloudContextFlight flight,
      FlightBeanBag appContext,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest) {
    flight.addStep(
        new DeleteControlledAzureResourcesStep(
            appContext.getResourceDao(),
            appContext.getControlledResourceService(),
            appContext.getSamService(),
            workspaceUuid,
            userRequest));
  }

  @Override
  public CloudContext makeCloudContextFromDb(DbCloudContext dbCloudContext) {
    return AzureCloudContext.deserialize(dbCloudContext);
  }

  /**
   * Retrieve the optional Azure cloud context for the given workspace The returned cloud context
   * may be in an intermediate state (creating, deleting, updating, broken)
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional Azure cloud context
   */
  @Traced
  public Optional<AzureCloudContext> getAzureCloudContext(UUID workspaceUuid) {
    return workspaceDao
        .getCloudContext(workspaceUuid, CloudPlatform.AZURE)
        .map(AzureCloudContext::deserialize);
  }

  /**
   * Retrieve the required AWS cloud context in the READY state for a given workspace
   *
   * @param workspaceUuid workspace id
   * @return AzureCloudContext
   */
  public AzureCloudContext getRequiredAzureCloudContext(UUID workspaceUuid) {
    AzureCloudContext cloudContext =
        getAzureCloudContext(workspaceUuid)
            .orElseThrow(
                () -> new CloudContextRequiredException("Operation requires Azure cloud context"));
    return cloudContext;
  }
}
