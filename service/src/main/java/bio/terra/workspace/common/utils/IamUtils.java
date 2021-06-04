package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class IamUtils {

  private IamUtils() {}

  /**
   * Extract a list of ControlledResourceIamRoles from the common fields of a controlled resource
   * request body, and validate that it's shaped appropriately for the specified AccessScopeType.
   *
   * <p>Shared access resources must not specify private resource roles. Private access resources
   * must specify at least one private resource role.
   */
  public static List<ControlledResourceIamRole> privateRolesFromBody(
      ApiControlledResourceCommonFields commonFields) {
    List<ControlledResourceIamRole> privateRoles =
        Optional.ofNullable(commonFields.getPrivateResourceUser())
            .map(
                user ->
                    user.getPrivateResourceIamRoles().stream()
                        .map(ControlledResourceIamRole::fromApiModel)
                        .collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    // Validate that we get the private role when the resource is private and do not get it
    // when the resource is public
    AccessScopeType accessScope = AccessScopeType.fromApi(commonFields.getAccessScope());
    if (accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE && privateRoles.isEmpty()) {
      throw new ValidationException("At least one IAM role is required for private resources");
    }
    if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED && !privateRoles.isEmpty()) {
      throw new ValidationException(
          "Private resource IAM roles are not allowed for shared resources");
    }
    return privateRoles;
  }
}
