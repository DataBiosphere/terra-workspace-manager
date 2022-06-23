package bio.terra.workspace.service.iam.model;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.generated.model.ApiIamRole;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Internal representation of IAM roles. */
public enum WsmIamRole {
  READER("reader", ApiIamRole.READER),
  WRITER("writer", ApiIamRole.WRITER),
  APPLICATION("application", ApiIamRole.APPLICATION),
  OWNER("owner", ApiIamRole.OWNER),
  // The manager role is given to WSM's SA on all Sam workspace objects for admin control. Users
  // are never given this role.
  MANAGER("manager", null);

  private final String samRole;
  private final ApiIamRole apiRole;

  WsmIamRole(String samRole, ApiIamRole apiRole) {
    this.samRole = samRole;
    this.apiRole = apiRole;
  }

  public static WsmIamRole fromApiModel(ApiIamRole apiModel) {
    Optional<WsmIamRole> result =
        Arrays.stream(WsmIamRole.values()).filter(x -> x.apiRole.equals(apiModel)).findFirst();
    return result.orElseThrow(
        () ->
            new RuntimeException(
                "No IamRole enum found corresponding to model role " + apiModel.toString()));
  }

  public static WsmIamRole fromSam(String samRole) {
    Optional<WsmIamRole> result =
        Arrays.stream(WsmIamRole.values()).filter(x -> x.samRole.equals(samRole)).findFirst();
    return result.orElseThrow(
        () -> new RuntimeException("No IamRole enum found corresponding to Sam role " + samRole));
  }

  public static WsmIamRole getHighestRole(UUID workspaceId, List<WsmIamRole> roles) {
    if (roles.isEmpty()) {
      throw new InternalServerErrorException(
          String.format("Workspace %s missing roles", workspaceId.toString()));
    }
    if (roles.contains(WsmIamRole.OWNER)) {
      return WsmIamRole.OWNER;
    } else if (roles.contains(WsmIamRole.WRITER)) {
      return WsmIamRole.WRITER;
    } else if (roles.contains(WsmIamRole.READER)) {
      return WsmIamRole.READER;
    }
    throw new InternalServerErrorException(
        String.format("Workspace %s has unexpected roles: %s", workspaceId.toString(), roles));
  }

  public ApiIamRole toApiModel() {
    return apiRole;
  }

  public String toSamRole() {
    return samRole;
  }
}
