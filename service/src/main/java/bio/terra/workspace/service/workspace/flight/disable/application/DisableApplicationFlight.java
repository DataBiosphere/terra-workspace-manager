package bio.terra.workspace.service.workspace.flight.disable.application;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.job.JobMapKeys;
import java.util.UUID;

/**
 * A {@link Flight} for disabling an application for a workspace. This flight deletes all resources
 * in the workspace for the application.
 */
public class DisableApplicationFlight extends Flight {

  public DisableApplicationFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    validateRequiredEntries(
        inputParameters,
        DisableApplicationKeys.WORKSPACE_ID,
        DisableApplicationKeys.APPLICATION_ID,
        JobMapKeys.AUTH_USER_INFO.getKeyName());

    FlightBeanBag beanBag = FlightBeanBag.getFromObject(applicationContext);
    UUID workspaceId =
        UUID.fromString(inputParameters.get(DisableApplicationKeys.WORKSPACE_ID, String.class));
    UUID applicationId =
        UUID.fromString(inputParameters.get(DisableApplicationKeys.APPLICATION_ID, String.class));
/* we will need this, but not yet
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
*/
    // We remove the metadata first, so that no new resources can be created by the application
    // while we are in the process of deleting them.
    addStep(new DisableApplicationDaoStep(beanBag.getApplicationDao(), workspaceId, applicationId));

    // TODO: code up the resource delete algorithm
  }
}
