package bio.terra.workspace.service.workspace.flight.application.able;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WsmApplicationKeys;
import java.util.List;
import java.util.UUID;

/**
 * This flight handles both enabling and disabling applications on workspaces. It is structured so
 * that if there is disagreement between Sam and our database, re-running the flight will fix that.
 * We run a "precheck" step to learn the current state and make sure we don't undo things that were
 * there before we started. Then we apply the Sam change and the database change.
 */
public class ApplicationAbleFlight extends Flight {
  public ApplicationAbleFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag beanBag = FlightBeanBag.getFromObject(applicationContext);

    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.WORKSPACE_ID,
        WorkspaceFlightMapKeys.APPLICATION_IDS,
        WsmApplicationKeys.APPLICATION_ABLE_ENUM);

    // get data from inputs that steps need
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    UUID workspaceUuid = inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    List<String> applicationIdList =
        inputParameters.get(WorkspaceFlightMapKeys.APPLICATION_IDS, List.class);
    AbleEnum ableEnum =
        inputParameters.get(WsmApplicationKeys.APPLICATION_ABLE_ENUM, AbleEnum.class);

    for (String applicationId : applicationIdList) {
      // ApplicationAblePrecheckStep
      // - make sure the application is in a valid state for the enable/disable
      // - check if the application is in the correct state the database
      // - check if the application is in the correct state in Sam
      // - remember the answers in the working map. For example, a value of 'true' in the
      //   APPLICATION_ABLE_DAO item means that the application is already in the correct state
      //   vis a vis the workspace. It does NOT mean the application is enabled.
      addStep(
          new ApplicationAblePrecheckStep(
              beanBag.getApplicationDao(),
              beanBag.getSamService(),
              userRequest,
              workspaceUuid,
              applicationId,
              ableEnum));

      // ApplicationEnableIamStep - On do, if application did not already have the role, add it.
      // On undo, if application did not already have the role, remove it.
      addStep(
          new ApplicationAbleIamStep(
              beanBag.getSamService(), userRequest, workspaceUuid, ableEnum));

      // ApplicationEnableDaoStep - On do, if application did not have the application enabled,
      // enable it. On undo, if application did not have the application enabled, disable it.
      // set the result
      addStep(
          new ApplicationAbleDaoStep(
              beanBag.getApplicationDao(), workspaceUuid, applicationId, ableEnum));
    }
  }
}
