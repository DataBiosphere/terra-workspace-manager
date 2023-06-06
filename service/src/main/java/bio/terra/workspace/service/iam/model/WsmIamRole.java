package bio.terra.workspace.service.iam.model;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.generated.model.ApiIamRole;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal representation of IAM roles. */
public enum WsmIamRole {
  DISCOVERER("discoverer", SamWorkspaceAction.DISCOVER, ApiIamRole.DISCOVERER) {
    public boolean roleAtLeastAsHighAs(WsmIamRole roleToCheck) {
      return true;
    }
  },
  READER("reader", SamWorkspaceAction.READ, ApiIamRole.READER) {
    public boolean roleAtLeastAsHighAs(WsmIamRole roleToCheck) {
      return roleToCheck == WsmIamRole.APPLICATION
          || roleToCheck == WsmIamRole.OWNER
          || roleToCheck == WsmIamRole.WRITER
          || roleToCheck == WsmIamRole.READER
          || roleToCheck == WsmIamRole.PROJECT_OWNER;
    }
  },
  WRITER("writer", SamWorkspaceAction.WRITE, ApiIamRole.WRITER) {
    public boolean roleAtLeastAsHighAs(WsmIamRole roleToCheck) {
      return roleToCheck == WsmIamRole.APPLICATION
          || roleToCheck == WsmIamRole.OWNER
          || roleToCheck == WsmIamRole.WRITER
          || roleToCheck == WsmIamRole.PROJECT_OWNER;
    }
  },
  APPLICATION("application", null, ApiIamRole.APPLICATION) {
    public boolean roleAtLeastAsHighAs(WsmIamRole roleToCheck) {
      return roleToCheck == WsmIamRole.APPLICATION;
    }
  },
  OWNER("owner", SamWorkspaceAction.OWN, ApiIamRole.OWNER) {
    public boolean roleAtLeastAsHighAs(WsmIamRole roleToCheck) {
      return roleToCheck == WsmIamRole.APPLICATION
          || roleToCheck == WsmIamRole.OWNER
          || roleToCheck == WsmIamRole.PROJECT_OWNER;
    }
  },
  // The manager role is given to WSM's SA on all Sam workspace objects for admin control. Users
  // are never given this role.
  MANAGER("manager", null, null) {
    public boolean roleAtLeastAsHighAs(WsmIamRole roleToCheck) {
      throw new InternalServerErrorException("Unexpected workspace MANAGER role");
    }
  },
  // Role for billing project owner so that owners of billing projects are able to view and manage/delete workspaces
  // using the project.
  PROJECT_OWNER("project-owner", SamWorkspaceAction.OWN, ApiIamRole.PROJECT_OWNER) {
    public boolean roleAtLeastAsHighAs(WsmIamRole roleToCheck) {
      return roleToCheck == WsmIamRole.APPLICATION || roleToCheck == WsmIamRole.PROJECT_OWNER;
    }
  };

  private static final Logger logger = LoggerFactory.getLogger(WsmIamRole.class);

  private final String samRole;
  private final String samAction;
  private final ApiIamRole apiRole;

  WsmIamRole(String samRole, String samAction, ApiIamRole apiRole) {
    this.samRole = samRole;
    this.samAction = samAction;
    this.apiRole = apiRole;
  }

  public static WsmIamRole fromApiModel(ApiIamRole apiModel) {
    Optional<WsmIamRole> result =
        Arrays.stream(WsmIamRole.values()).filter(x -> x.apiRole.equals(apiModel)).findFirst();
    return result.orElseThrow(
        () ->
            new RuntimeException("No IamRole enum found corresponding to model role " + apiModel));
  }

  /**
   * Return the WsmIamRole corresponding to the provided Sam role, or null if the Sam role does not
   * match a Wsm role. There are roles on workspaces in Sam that are used by Rawls and not WSM -- in
   * general WSM should ignore these roles.
   */
  public static WsmIamRole fromSam(String samRole) {
    return Arrays.stream(WsmIamRole.values())
        .filter(x -> x.samRole.equals(samRole))
        .findFirst()
        .orElse(null);
  }

  public static Optional<WsmIamRole> getHighestRole(UUID workspaceId, List<WsmIamRole> roles) {
    if (roles.isEmpty()) {
      // This workspace had a role that this WSM doesn't know about.
      logger.warn("Workspace {} missing roles", workspaceId);
      return Optional.empty();
    }

    if (roles.contains(WsmIamRole.APPLICATION)) {
      return Optional.of(WsmIamRole.APPLICATION);
    } else if (roles.contains(WsmIamRole.PROJECT_OWNER)) {
      return Optional.of(WsmIamRole.PROJECT_OWNER);
    } else if (roles.contains(WsmIamRole.OWNER)) {
      return Optional.of(WsmIamRole.OWNER);
    } else if (roles.contains(WsmIamRole.WRITER)) {
      return Optional.of(WsmIamRole.WRITER);
    } else if (roles.contains(WsmIamRole.READER)) {
      return Optional.of(WsmIamRole.READER);
    } else if (roles.contains(WsmIamRole.DISCOVERER)) {
      return Optional.of(WsmIamRole.DISCOVERER);
    }
    throw new InternalServerErrorException(
        String.format("Workspace %s has unexpected roles: %s", workspaceId, roles));
  }

  public abstract boolean roleAtLeastAsHighAs(WsmIamRole roleToCheck);

  public ApiIamRole toApiModel() {
    return apiRole;
  }

  public String toSamRole() {
    return samRole;
  }

  public String toSamAction() {
    if (samAction == null) {
      throw new InternalServerErrorException("toSamAction called for " + name());
    }
    return samAction;
  }
}
