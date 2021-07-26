package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.EnumNotRecognizedException;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import java.util.Collections;
import java.util.List;

public final class IamRoleUtils {

  private IamRoleUtils() {}

  /**
   * Build the list of IAM roles for this user. If the resource was initially shared, we make the
   * cloned resource shared as well. If it's private, the user making the clone must be the resource
   * user and becomes EDITOR, READER, and WRITER on the new resource.
   *
   * @param accessScope - private vs shared access
   * @return list of IAM roles for the user on the resource
   */
  public static List<ControlledResourceIamRole> getIamRolesForAccessScope(
      AccessScopeType accessScope) {
    switch (accessScope) {
      case ACCESS_SCOPE_SHARED:
        return Collections.emptyList();
      case ACCESS_SCOPE_PRIVATE:
        // User owns the cloned private resource completely
        return List.of(
            ControlledResourceIamRole.READER,
            ControlledResourceIamRole.WRITER,
            ControlledResourceIamRole.EDITOR);
      default:
        throw new EnumNotRecognizedException(
            String.format("Access Scope %s is not recognized.", accessScope.toString()));
    }
  }
}
