package bio.terra.workspace.service.workspace;

import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.CreateCustomGcpRolesStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.CreatePetSaStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.DeleteControlledDbResourcesStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.DeleteControlledSamResourcesStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.DeleteGcpProjectStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.GcpCloudSyncStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.GenerateRbsRequestIdStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.GrantWsmRoleAdminStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.PullProjectFromPoolStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.SetProjectBillingStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.SyncSamGroupsStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.WaitForProjectPermissionsStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service provides methods for managing GCP cloud context in the WSM database. These methods
 * do not perform any access control and operate directly against the {@link
 * bio.terra.workspace.db.WorkspaceDao}
 */
@SuppressFBWarnings(
    value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
    justification = "Enable both injection and static lookup")
@Component
public class GcpCloudContextService implements CloudContextService {
  private static GcpCloudContextService theService;

  private final WorkspaceDao workspaceDao;

  @Autowired
  public GcpCloudContextService(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  // Set up static accessor for use by CloudPlatform
  @PostConstruct
  public void postConstruct() {
    theService = this;
  }

  public static GcpCloudContextService getTheService() {
    return theService;
  }

  @Override
  public void addCreateCloudContextSteps(
      CreateCloudContextFlight flight,
      FlightBeanBag appContext,
      UUID workspaceUuid,
      SpendProfile spendProfile,
      AuthenticatedUserRequest userRequest) {

    GcpCloudSyncRoleMapping gcpCloudSyncRoleMapping = appContext.getCloudSyncRoleMapping();
    CrlService crl = appContext.getCrlService();
    RetryRule shortRetry = RetryRules.shortExponential();
    RetryRule cloudRetry = RetryRules.cloud();
    RetryRule bufferRetry = RetryRules.buffer();

    // Allocate the GCP project from RBS by generating the id and then getting the project.
    flight.addStep(new GenerateRbsRequestIdStep());
    flight.addStep(
        new PullProjectFromPoolStep(
            appContext.getBufferService(), crl.getCloudResourceManagerCow()),
        bufferRetry);

    // Configure the project for WSM
    flight.addStep(
        new SetProjectBillingStep(crl.getCloudBillingClientCow(), spendProfile), cloudRetry);
    flight.addStep(new GrantWsmRoleAdminStep(crl), shortRetry);
    flight.addStep(
        new CreateCustomGcpRolesStep(gcpCloudSyncRoleMapping, crl.getIamCow()), shortRetry);
    // Create the pet before sync'ing, so the proxy group is configured before we
    // do the Sam sync and create the role-based Google groups. That eliminates
    // one propagation case
    flight.addStep(new CreatePetSaStep(appContext.getSamService(), userRequest), shortRetry);
    flight.addStep(
        new SyncSamGroupsStep(appContext.getSamService(), workspaceUuid, spendProfile, userRequest),
        shortRetry);

    flight.addStep(
        new GcpCloudSyncStep(
            crl.getCloudResourceManagerCow(),
            gcpCloudSyncRoleMapping,
            appContext.getFeatureConfiguration(),
            appContext.getSamService(),
            appContext.getGrantService(),
            userRequest,
            workspaceUuid),
        bufferRetry);

    // Wait for the project permissions to propagate.
    // The SLO is 99.5% of the time it finishes in under 7 minutes.
    flight.addStep(new WaitForProjectPermissionsStep());
  }

  @Override
  public void addDeleteCloudContextSteps(
      DeleteCloudContextFlight flight,
      FlightBeanBag appContext,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest) {
    RetryRule retryRule = RetryRules.cloudLongRunning();

    // We delete controlled resources from Sam and WSM databases, but do not need to delete the
    // actual cloud objects, as GCP handles the cleanup when we delete the containing project.
    flight.addStep(
        new DeleteControlledSamResourcesStep(
            appContext.getSamService(),
            appContext.getResourceDao(),
            workspaceUuid,
            CloudPlatform.GCP),
        retryRule);
    flight.addStep(
        new DeleteControlledDbResourcesStep(
            appContext.getResourceDao(), workspaceUuid, CloudPlatform.GCP),
        retryRule);
    flight.addStep(
        new DeleteGcpProjectStep(
            appContext.getCrlService(), appContext.getGcpCloudContextService()),
        retryRule);
  }

  @Override
  public CloudContext makeCloudContextFromDb(DbCloudContext dbCloudContext) {
    return GcpCloudContext.deserialize(dbCloudContext);
  }

  /**
   * Retrieve the optional GCP cloud context
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional GCP cloud context
   */
  @Traced
  public Optional<GcpCloudContext> getGcpCloudContext(UUID workspaceUuid) {
    return workspaceDao
        .getCloudContext(workspaceUuid, CloudPlatform.GCP)
        .map(GcpCloudContext::deserialize);
  }

  /**
   * Retrieve the GCP cloud context. This is used during controlled resource create.
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return GCP cloud context with all policies filled in.
   */
  public GcpCloudContext getRequiredGcpCloudContext(UUID workspaceUuid) {
    GcpCloudContext cloudContext =
        getGcpCloudContext(workspaceUuid)
            .orElseThrow(
                () -> new CloudContextRequiredException("Operation requires GCP cloud context"));
    return cloudContext;
  }

  /**
   * Helper method for looking up the GCP project ID for a given workspace ID, if one exists. Unlike
   * {@link #getRequiredGcpProject}, this returns an empty Optional instead of throwing if the given
   * workspace does not have a GCP cloud context.
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional GCP project from the cloud context
   */
  public Optional<String> getGcpProject(UUID workspaceUuid) {
    return getGcpCloudContext(workspaceUuid).map(GcpCloudContext::getGcpProjectId);
  }

  /**
   * Helper method used by other classes that require the GCP project to exist in the workspace. It
   * throws if the project (GCP cloud context) is not set up.
   *
   * @param workspaceUuid unique workspace id
   * @return GCP project id
   */
  public String getRequiredGcpProject(UUID workspaceUuid) {
    GcpCloudContext cloudContext = getRequiredGcpCloudContext(workspaceUuid);
    return cloudContext.getGcpProjectId();
  }
}
