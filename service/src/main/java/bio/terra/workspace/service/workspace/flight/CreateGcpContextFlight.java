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
public class CreateGcpContextFlight extends Flight {
  // Buffer Retry rule settings. For Buffer Service, allow for long wait times.
  // If the pool is empty, Buffer Service may need time to actually create a new project.

  public CreateGcpContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    CrlService crl = appContext.getCrlService();

    RetryRule bufferRetryRule = RetryRules.buffer();

    UUID workspaceId =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    RetryRule retryRule = RetryRules.shortExponential();
    addStep(
        new CheckSpendProfileStep(
            appContext.getWorkspaceDao(),
            appContext.getSpendProfileService(),
            workspaceId,
            userRequest));
    addStep(new GenerateProjectIdStep());
    addStep(
        new PullProjectFromPoolStep(
            appContext.getBufferService(), crl.getCloudResourceManagerCow()),
        bufferRetryRule);

    addStep(new SetProjectBillingStep(crl.getCloudBillingClientCow()), RetryRules.cloud());
    addStep(new GrantWsmRoleAdminStep(crl), retryRule);
    addStep(new CreateCustomGcpRolesStep(crl.getIamCow()), retryRule);
    addStep(
        new StoreGcpContextStep(appContext.getGcpCloudContextService(), workspaceId), retryRule);
    addStep(new SyncSamGroupsStep(appContext.getSamService(), workspaceId, userRequest), retryRule);
    addStep(new GcpCloudSyncStep(crl.getCloudResourceManagerCow()), retryRule);
    addStep(new SetGcpContextOutputStep());
  }
}
