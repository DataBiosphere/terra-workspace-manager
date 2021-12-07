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
 * create the project. This is V2 of the flight. It is a separate version so that we do not break a
 * running V1 of the flight during an upgrade.
 *
 * <p>This flight includes two changes:
 *
 * <ol>
 *   <li>Cloud context locking. We write the cloud context row into the database in an incomplete
 *       form to prevent concurrent, conflicting cloud context creations
 *   <li>Store sync'd workspace policy groups in the cloud context. These fixed group names can be
 *       reused from WSM data rather than requesting them from Sam during controlled resource
 *       created.
 * </ol>
 *
 * In order to add items to the cloud context, a version 2 of the serialized cloud context form is
 * used.
 */
public class CreateGcpContextFlightV2 extends Flight {

  public CreateGcpContextFlightV2(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    CrlService crl = appContext.getCrlService();

    UUID workspaceId =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    RetryRule shortRetry = RetryRules.shortExponential();
    RetryRule cloudRetry = RetryRules.cloud();

    // Check that we are allowed to spend money. No point doing anything else unless that
    // is true.
    addStep(
        new CheckSpendProfileStep(
            appContext.getWorkspaceDao(),
            appContext.getSpendProfileService(),
            workspaceId,
            userRequest));

    // Write the cloud context row in a "locked" state
    addStep(
        new CreateAndLockDbGcpCloudContextStep(workspaceId, appContext.getGcpCloudContextService()),
        shortRetry);

    // Allocate the GCP project from RBS by generating the id and then getting the project.
    addStep(new GenerateProjectIdStep());
    addStep(
        new PullProjectFromPoolStep(
            appContext.getBufferService(), crl.getCloudResourceManagerCow()),
        RetryRules.buffer());

    // Configure the project for WSM
    addStep(new SetProjectBillingStep(crl.getCloudBillingClientCow()), cloudRetry);
    addStep(new GrantWsmRoleAdminStep(crl), shortRetry);
    addStep(new CreateCustomGcpRolesStep(crl.getIamCow()), shortRetry);
    addStep(
        new SyncSamGroupsStep(appContext.getSamService(), workspaceId, userRequest), shortRetry);
    addStep(new GcpCloudSyncStep(crl.getCloudResourceManagerCow()), cloudRetry);

    // Store the cloud context data and unlock the database row
    // This must be the last step, since it clears the lock. So this step also
    // sets the flight response.
    addStep(
        new UpdateAndUnlockDbGcpCloudContextStep(
            workspaceId, appContext.getGcpCloudContextService()),
        shortRetry);
  }
}
