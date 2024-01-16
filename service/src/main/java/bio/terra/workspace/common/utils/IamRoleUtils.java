package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.EnumNotRecognizedException;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;

public final class IamRoleUtils {

  private IamRoleUtils() {}

  // TODO: [PF-1214] this code seems orphaned here. It is only called from cloning of
  //  controlled resources, so doesn't seem like common/utils is the right spot.
  /**
   * Determine the IAM role for this user. If the resource was initially shared, we make the cloned
   * resource shared as well. If it's private, the user making the clone must be the resource user
   * and becomes EDITOR.
   *
   * @param accessScope - private vs shared access
   * @return IAM role for the user on the resource
   */
  public static ControlledResourceIamRole getIamRoleForAccessScope(AccessScopeType accessScope) {
    return switch (accessScope) {
      case ACCESS_SCOPE_SHARED -> null;
      case ACCESS_SCOPE_PRIVATE ->
          // User owns the cloned private resource completely
          ControlledResourceIamRole.EDITOR;
      default ->
          throw new EnumNotRecognizedException(
              String.format("Access Scope %s is not recognized.", accessScope));
    };
  }
}
