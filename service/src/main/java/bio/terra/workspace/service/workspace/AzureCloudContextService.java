package bio.terra.workspace.service.workspace;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.PolicyValidator;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.flight.cloud.azure.ValidateLandingZoneAgainstPolicyStep;
import bio.terra.workspace.service.workspace.flight.cloud.azure.ValidateMRGStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service provides methods for managing Azure cloud context. These methods do not perform any
 * access control and operate directly against the {@link WorkspaceDao}
 */
@Component
public class AzureCloudContextService implements CloudContextService {
  private static AzureCloudContextService theService;

  private final WorkspaceDao workspaceDao;
  private final ResourceDao resourceDao;
  private final FeatureConfiguration featureConfiguration;
  private final WorkspaceService workspaceService;
  private final PolicyValidator policyValidator;

  @Autowired
  public AzureCloudContextService(
      WorkspaceDao workspaceDao,
      ResourceDao resourceDao,
      FeatureConfiguration featureConfiguration,
      WorkspaceService workspaceService,
      PolicyValidator policyValidator) {
    this.workspaceDao = workspaceDao;
    this.resourceDao = resourceDao;
    this.featureConfiguration = featureConfiguration;
    this.workspaceService = workspaceService;
    this.policyValidator = policyValidator;
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
          new ValidateLandingZoneAgainstPolicyStep(
              appContext.getLandingZoneApiDispatch(),
              userRequest,
              appContext.getTpsApiDispatch(),
              workspaceUuid,
              workspaceService,
              policyValidator));
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
    // No post-resource delete steps for Azure
  }

  @Override
  public CloudContext makeCloudContextFromDb(DbCloudContext dbCloudContext) {
    return AzureCloudContext.deserialize(dbCloudContext);
  }

  @Override
  public List<ControlledResource> makeOrderedResourceList(UUID workspaceUuid) {
    return resourceDao.listControlledResources(workspaceUuid, CloudPlatform.AZURE);
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

  /**
   * Retrieve the optional Azure cloud context
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional Azure cloud context
   */
  @WithSpan
  public Optional<AzureCloudContext> getAzureCloudContext(UUID workspaceUuid) {
    return workspaceDao
        .getCloudContext(workspaceUuid, CloudPlatform.AZURE)
        .map(AzureCloudContext::deserialize);
  }

  public AzureCloudContext getRequiredAzureCloudContext(UUID workspaceUuid) {
    return getAzureCloudContext(workspaceUuid)
        .orElseThrow(
            () -> new CloudContextRequiredException("Operation requires Azure cloud context"));
  }
}
