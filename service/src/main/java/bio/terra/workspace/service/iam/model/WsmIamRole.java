package bio.terra.workspace.service.iam.model;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.generated.model.ApiIamRole;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal representation of IAM roles. */
public enum WsmIamRole {
  READER("reader", ApiIamRole.READER),
  WRITER("writer", ApiIamRole.WRITER),
  APPLICATION("application", ApiIamRole.APPLICATION),
  OWNER("owner", ApiIamRole.OWNER),
  // The manager role is given to WSM's SA on all Sam workspace objects for admin control. Users
  // are never given this role.
  MANAGER("manager", null);

  private static final Logger logger = LoggerFactory.getLogger(WsmIamRole.class);

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

  /**
   * Return the WsmIamRole corresponding to the provided Sam role, or null if the Sam role does not
   * match a Wsm role. There are valid roles on workspaces in Sam which do not map to WsmIam roles,
   * in general WSM should ignore these roles.
   */
  public static WsmIamRole fromSam(String samRole) {
    return Arrays.stream(WsmIamRole.values())
        .filter(x -> x.samRole.equals(samRole))
        .findFirst()
        .orElse(null);
  }

  public static Optional<WsmIamRole> getHighestRole(UUID workspaceId, List<WsmIamRole> roles) {
    if (roles.isEmpty()) {
      // This should be extremely rare. This only happens when a WSM role has been added to SAM,
      // but WSM doesn't know about it yet (eg a local WSM created a workspace with this role, but
      // broad-dev WSM doesn't know about role yet).
      logger.warn("Workspace %s missing roles", workspaceId.toString());
      return Optional.empty();
    }

    if (roles.contains(WsmIamRole.APPLICATION)) {
      return Optional.of(WsmIamRole.APPLICATION);
    } else if (roles.contains(WsmIamRole.OWNER)) {
      return Optional.of(WsmIamRole.OWNER);
    } else if (roles.contains(WsmIamRole.WRITER)) {
      return Optional.of(WsmIamRole.WRITER);
    } else if (roles.contains(WsmIamRole.READER)) {
      return Optional.of(WsmIamRole.READER);
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
