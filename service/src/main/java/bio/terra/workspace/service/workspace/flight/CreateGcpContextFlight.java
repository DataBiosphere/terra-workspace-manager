package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import java.util.UUID;

/**
 * A {@link Flight} for creating a Google cloud context for a workspace using Buffer Service to
 * create the project.
 */
@Deprecated // TODO: PF-1238 remove
public class CreateGcpContextFlight extends Flight {
  // Buffer Retry rule settings. For Buffer Service, allow for long wait times.
  // If the pool is empty, Buffer Service may need time to actually create a new project.

  public CreateGcpContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    CrlService crl = appContext.getCrlService();

    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    RetryRule dbRetry = RetryRules.shortDatabase();
    RetryRule shortRetry = RetryRules.shortExponential();
    RetryRule cloudRetry = RetryRules.cloud();

    addStep(
        new CheckSpendProfileStep(
            appContext.getWorkspaceDao(),
            appContext.getSpendProfileService(),
            workspaceUuid,
            userRequest));
    addStep(new GenerateRbsRequestIdStep());
    addStep(
        new PullProjectFromPoolStep(
            appContext.getBufferService(), crl.getCloudResourceManagerCow()),
        RetryRules.buffer());

    addStep(new SetProjectBillingStep(crl.getCloudBillingClientCow()), cloudRetry);
    addStep(new GrantWsmRoleAdminStep(crl), shortRetry);
    addStep(new CreateCustomGcpRolesStep(crl.getIamCow()), shortRetry);
    addStep(
        new SyncSamGroupsStep(appContext.getSamService(), workspaceUuid, userRequest), shortRetry);
    addStep(new GcpCloudSyncStep(crl.getCloudResourceManagerCow()), cloudRetry);
    addStep(new StoreGcpContextStep(appContext.getGcpCloudContextService(), workspaceUuid), dbRetry);
    addStep(new SetGcpContextOutputStep());
  }
}
