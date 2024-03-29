package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.flightGenerator.FlightGenerator;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WsmApplicationKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;

/**
 * This flight handles both enabling and disabling applications on workspaces. It is structured so
 * that if there is disagreement between Sam and our database, re-running the flight will fix that.
 * We run a "precheck" step to learn the current state and make sure we don't undo things that were
 * there before we started. Then we apply the Sam change and the database change.
 */
public class ApplicationAbleFlightV2 extends FlightGenerator {
  public ApplicationAbleFlightV2(FlightMap inputParameters, Object applicationContext) {
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
        inputParameters.get(WorkspaceFlightMapKeys.APPLICATION_IDS, new TypeReference<>() {});
    AbleEnum ableEnum =
        inputParameters.get(WsmApplicationKeys.APPLICATION_ABLE_ENUM, AbleEnum.class);

    var applicationAbleSupport = beanBag.getBean(ApplicationAbleSupport.class);

    try {
      for (String applicationId : applicationIdList) {
        var applicationInfo =
            createStep(applicationAbleSupport)
                .getApplicationInfo(userRequest, workspaceUuid, applicationId, ableEnum);

        createStep(applicationAbleSupport).checkApplicationState(applicationInfo, ableEnum);

        createStep(applicationAbleSupport)
            .updateIamStep(userRequest, workspaceUuid, ableEnum, applicationInfo);

        var results =
            createStep(applicationAbleSupport)
                .updateDatabaseStep(workspaceUuid, applicationId, ableEnum, applicationInfo);

        setResponse(results.application());
      }
    } catch (InterruptedException e) {
      // This should not happen because is not actually running the steps yet
      throw new RuntimeException("Interrupted setting up application enable/disable flight", e);
    }
  }
}
