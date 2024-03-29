package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.common.flightGenerator.NoRetry;
import bio.terra.workspace.common.flightGenerator.NoUndo;
import bio.terra.workspace.common.flightGenerator.UndoMethod;
import bio.terra.workspace.db.exception.InvalidApplicationStateException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import java.util.UUID;

public interface ApplicationAbleSupport {

  /** gather information about the application and its current state */
  @NoRetry
  @NoUndo
  ApplicationInfo getApplicationInfo(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      String applicationId,
      AbleEnum ableEnum);

  /** if ableEnum is ENABLE, make sure application state is OPERATING */
  @NoRetry
  @NoUndo
  default void checkApplicationState(ApplicationInfo applicationInfo, AbleEnum ableEnum) {
    // For enable, we require that the application be in the operating state
    if (ableEnum == AbleEnum.ENABLE
        && applicationInfo.application().getState() != WsmApplicationState.OPERATING) {
      throw new InvalidApplicationStateException(
          "Applications is "
              + applicationInfo.application().getState().toApi()
              + " and cannot be enabled");
    }
  }

  /** if application did not already have the role, add it. */
  @NoRetry
  @UndoMethod("undoUpdateIamStep")
  void updateIamStep(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      AbleEnum ableEnum,
      ApplicationInfo applicationInfo)
      throws InterruptedException;

  /** if application did not already have the role, remove it */
  void undoUpdateIamStep(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      AbleEnum ableEnum,
      ApplicationInfo applicationInfo)
      throws InterruptedException;

  @NoRetry
  @UndoMethod("undoUpdateDatabaseStep")
  ApplicationAbleResult updateDatabaseStep(
      UUID workspaceUuid, String applicationId, AbleEnum ableEnum, ApplicationInfo applicationInfo);

  void undoUpdateDatabaseStep(
      UUID workspaceUuid, String applicationId, AbleEnum ableEnum, ApplicationInfo applicationInfo);
}
